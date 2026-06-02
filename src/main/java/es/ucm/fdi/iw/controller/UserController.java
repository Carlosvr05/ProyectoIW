package es.ucm.fdi.iw.controller;

import es.ucm.fdi.iw.LocalData;
import es.ucm.fdi.iw.model.Message;
import es.ucm.fdi.iw.model.Transferable;
import es.ucm.fdi.iw.model.User;
import es.ucm.fdi.iw.model.Pedido;
import es.ucm.fdi.iw.model.User.Role;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.FileCopyUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import jakarta.persistence.EntityManager;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.transaction.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.*;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Controlador de Usuarios.
 * Gestiona los perfiles, la seguridad (encriptación de contraseñas), fotos de
 * avatar,
 * historial de pedidos personales y la mensajería privada (chat individual).
 * El acceso está restringido a usuarios autenticados.
 */
@Controller()
@RequestMapping("user")
public class UserController {

  private static final Logger log = LogManager.getLogger(UserController.class);

  @Autowired
  private EntityManager entityManager;

  @Autowired
  private LocalData localData;

  @Autowired
  private SimpMessagingTemplate messagingTemplate;

  @Autowired
  private PasswordEncoder passwordEncoder;

  /**
   * Pasa datos de la sesión HTTP al Modelo de Thymeleaf automáticamente.
   * Evita repetir inyecciones de sesión en cada endpoint.
   */
  @ModelAttribute
  public void populateModel(HttpSession session, Model model) {
    for (String name : new String[] { "u", "url", "ws", "topics" }) {
      model.addAttribute(name, session.getAttribute(name));
    }
  }

  /**
   * Excepción personalizada lanzada cuando un usuario intenta acceder/modificar
   * el perfil de otra persona sin tener permisos de Administrador.
   * Devuelve un código HTTP 403 Forbidden.
   */
  @ResponseStatus(value = HttpStatus.FORBIDDEN, reason = "No eres administrador, y éste no es tu perfil") // 403
  public static class NoEsTuPerfilException extends RuntimeException {
  }

  /**
   * Codifica una contraseña en texto plano de forma segura usando BCrypt.
   * La misma contraseña codificada varias veces generará diferentes hashes
   * gracias al uso de 'salts' aleatorias.
   * 
   * @param rawPassword Contraseña en texto plano a encriptar
   * @return Hash seguro (string de 60 caracteres) possible encoding of "test" is
   *         {bcrypt}$2y$12$XCKz0zjXAP6hsFyVc8MucOzx6ER6IsC1qo5zQbclxhddR1t6SfrHm
   */
  public String encodePassword(String rawPassword) {
    return passwordEncoder.encode(rawPassword);
  }

  /**
   * Genera un token aleatorio seguro codificado en Base64.
   * Útil para generar contraseñas temporales o identificadores de sesión.
   * 
   * @param byteLength Longitud del token
   * @return String en base64
   */
  public static String generateRandomBase64Token(int byteLength) {
    SecureRandom secureRandom = new SecureRandom();
    byte[] token = new byte[byteLength];
    secureRandom.nextBytes(token);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(token); // base64 encoding
  }

  /**
   * Carga y muestra la página de perfil personal de un usuario concreto.
   * Además de sus datos, consulta y pasa a la vista todos los pedidos
   * (historial de compras) que ha realizado.
   */
  @GetMapping("{id}")
  public String index(@PathVariable long id, Model model, HttpSession session) {
    User target = entityManager.find(User.class, id);
    model.addAttribute("user", target);

    // Obtener el historial completo de pedidos de este usuario (solo si es su
    // perfil o es admin)
    User requester = (User) session.getAttribute("u");
    if (requester != null && (requester.getId() == target.getId() || requester.hasRole(Role.ADMIN))) {
      List<?> pedidos = entityManager
          .createQuery(
              "SELECT p FROM Pedido p WHERE p.cliente.id = :uid AND (p.visible IS NULL OR p.visible = true) ORDER BY p.fechaCompra DESC")
          .setParameter("uid", target.getId())
          .getResultList();
      model.addAttribute("pedidos", pedidos);
    }

    return "user"; // Renderiza user.html
  }

  /**
   * Modifica los datos de un usuario existente o crea uno nuevo (sólo admins).
   */
  @PostMapping("/{id}")
  @Transactional
  public String postUser(
      HttpServletResponse response,
      @PathVariable long id,
      @ModelAttribute User edited,
      @RequestParam(required = false) String pass2,
      Model model, HttpSession session) throws IOException {

    User requester = (User) session.getAttribute("u");
    User target = null;

    // Lógica para crear un nuevo usuario si se envía id=-1 (exclusivo para Admins)
    if (id == -1 && requester.hasRole(Role.ADMIN)) {
      target = new User();
      target.setPassword(encodePassword(generateRandomBase64Token(12))); // Genera pass aleatoria
      target.setEnabled(true);
      entityManager.persist(target);
      entityManager.flush(); // Forzar para obtener el id
      id = target.getId();
    }

    // Recupera al usuario que queremos modificar
    target = entityManager.find(User.class, id);
    model.addAttribute("user", target);

    // Protección de seguridad: Nadie puede editar el perfil ajeno (salvo Admins)
    if (requester.getId() != target.getId() && !requester.hasRole(Role.ADMIN)) {
      throw new NoEsTuPerfilException();
    }

    // Lógica de cambio de contraseña
    if (edited.getPassword() != null) {
      if (!edited.getPassword().equals(pass2)) {
        log.warn("Passwords do not match - returning to user form");
        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        model.addAttribute("user", target);
        return "user"; // Falla y recarga si las contraseñas no coinciden
      } else {
        // Encripta la nueva contraseña introducida
        target.setPassword(encodePassword(edited.getPassword()));
      }
    }

    // Actualiza los demás campos
    target.setUsername(edited.getUsername());
    target.setFirstName(edited.getFirstName());
    target.setLastName(edited.getLastName());

    // Si el usuario se está modificando a sí mismo, actualizar también su sesión en
    // memoria
    if (requester.getId() == target.getId()) {
      session.setAttribute("u", target);
    }

    return "user";
  }

  /**
   * Devuelve la imagen de perfil por defecto si el usuario no tiene ninguna.
   */
  private static InputStream defaultPic() {
    return new BufferedInputStream(Objects.requireNonNull(
        UserController.class.getClassLoader().getResourceAsStream(
            "static/img/default-pic.jpg")));
  }

  /**
   * Sirve la foto de perfil particular de un usuario a través de un stream
   * (directo al HTML).
   */
  @GetMapping("{id}/pic")
  public StreamingResponseBody getPic(@PathVariable long id) throws IOException {
    File f = localData.getFile("user", "" + id + ".jpg");
    InputStream in = new BufferedInputStream(f.exists() ? new FileInputStream(f) : UserController.defaultPic());
    return os -> FileCopyUtils.copy(in, os);
  }

  /**
   * Sube o cambia la fotografía de perfil de un usuario.
   * Requiere pertenecer al usuario dueño de la foto, o tener rol ADMIN.
   */
  @PostMapping("{id}/pic")
  @ResponseBody
  public String setPic(@RequestParam("photo") MultipartFile photo, @PathVariable long id,
      HttpServletResponse response, HttpSession session, Model model) throws IOException {

    User target = entityManager.find(User.class, id);
    model.addAttribute("user", target);

    // Validación estricta de permisos
    User requester = (User) session.getAttribute("u");
    if (requester.getId() != target.getId() && !requester.hasRole(Role.ADMIN)) {
      throw new NoEsTuPerfilException();
    }

    log.info("Updating photo for user {}", id);
    File f = localData.getFile("user", "" + id + ".jpg");
    if (photo.isEmpty()) {
      log.info("failed to upload photo: emtpy file?");
    } else {
      try (BufferedOutputStream stream = new BufferedOutputStream(new FileOutputStream(f))) {
        byte[] bytes = photo.getBytes();
        stream.write(bytes);
        log.info("Uploaded photo for {} into {}!", id, f.getAbsolutePath());
      } catch (Exception e) {
        response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        log.warn("Error uploading " + id + " ", e);
      }
    }
    return "{\"status\":\"photo uploaded correctly\"}"; // Responder con JSON éxito
  }

  /**
   * Manejador genérico de errores bajo el endpoint "/user/error".
   */
  @GetMapping("error")
  public String error(Model model, HttpSession session, HttpServletRequest request) {
    model.addAttribute("sess", session);
    model.addAttribute("req", request);
    return "error";
  }

  /**
   * Recupera todos los mensajes directos (privados) RECIBIDOS por el usuario
   * autenticado actualmente en la sesión, devolviéndolos en JSON.
   */
  @GetMapping(path = "received", produces = "application/json")
  @Transactional // para no recibir resultados inconsistentes
  @ResponseBody // para indicar que no devuelve vista, sino un objeto (jsonizado)
  public List<Message.Transfer> retrieveMessages(HttpSession session) {
    long userId = ((User) session.getAttribute("u")).getId();
    User u = entityManager.find(User.class, userId);
    log.info("Generating message list for user {} ({} messages)",
        u.getUsername(), u.getReceived().size());

    // Convierte las Entidades Message a objetos DTO ligeros (Transfer)
    return u.getReceived().stream().map(Transferable::toTransfer).collect(Collectors.toList());
  }

  /**
   * Devuelve cuántos mensajes directos no han sido leídos por el usuario actual
   * (JSON).
   */
  @GetMapping(path = "unread", produces = "application/json")
  @ResponseBody
  public String checkUnread(HttpSession session) {
    long userId = ((User) session.getAttribute("u")).getId();
    long unread = entityManager.createNamedQuery("Message.countUnread", Long.class)
        .setParameter("userId", userId)
        .getSingleResult();

    // Actualiza también la variable en la sesión global
    session.setAttribute("unread", unread);
    return "{\"unread\": " + unread + "}";
  }

  /**
   * Envía un mensaje directo (privado) a un usuario concreto, registrándolo en BD
   * y empujándolo por WebSockets para que lo vea en tiempo real sin recargar
   * página.
   * 
   * @param id ID del usuario destinatario
   * @param o  Objeto JSON con el texto del mensaje
   */
  @PostMapping("/{id}/msg")
  @ResponseBody
  @Transactional
  public String postMsg(@PathVariable long id,
      @RequestBody JsonNode o, Model model, HttpSession session)
      throws JsonProcessingException {

    String text = o.get("message").asText();
    User u = entityManager.find(User.class, id);
    User sender = entityManager.find(User.class, ((User) session.getAttribute("u")).getId());
    model.addAttribute("user", u);

    // 1. Construye el mensaje y lo asocia en la BD a destinatario y remitente
    Message m = new Message();
    m.setRecipient(u);
    m.setSender(sender);
    m.setDateSent(LocalDateTime.now());
    m.setText(text);
    entityManager.persist(m);
    entityManager.flush(); // Fuerza guardar para obtener el ID real

    ObjectMapper mapper = new ObjectMapper();
    /*
     * // construye json: método manual
     * ObjectNode rootNode = mapper.createObjectNode();
     * rootNode.put("from", sender.getUsername());
     * rootNode.put("to", u.getUsername());
     * rootNode.put("text", text);
     * rootNode.put("id", m.getId());
     * String json = mapper.writeValueAsString(rootNode);
     */
    // persiste objeto a json usando Jackson
    String json = mapper.writeValueAsString(m.toTransfer());

    log.info("Sending a message to {} with contents '{}'", id, json);

    // 3. Empuja la notificación en tiempo real a la cola websocket de ese usuario
    messagingTemplate.convertAndSend("/user/" + u.getUsername() + "/queue/updates", json);
    return "{\"result\": \"message sent.\"}";
  }

  /**
   * Borra un pedido del historial del usuario.
   */
  @PostMapping("/pedidos/{pedidoId}/delete")
  @Transactional
  public String deletePedido(@PathVariable long pedidoId, HttpSession session) {
    User requester = (User) session.getAttribute("u");
    Pedido p = entityManager.find(Pedido.class, pedidoId);
    if (p != null && requester != null
        && (p.getCliente().getId() == requester.getId() || requester.hasRole(Role.ADMIN))) {
      // Borrar las líneas del pedido primero por la restricción de clave foránea
      for (es.ucm.fdi.iw.model.LineaPedido lp : p.getLineas()) {
        entityManager.remove(lp);
      }
      // Borrar el pedido
      entityManager.remove(p);
    }
    return "redirect:/user/" + (p != null ? p.getCliente().getId() : requester.getId()) + "#pedidos";
  }

  /**
   * Añade 1000€ al saldo del usuario (solo para usuarios normales).
   */
  @PostMapping("/add-money")
  @Transactional
  public String addMoney(HttpSession session, jakarta.servlet.http.HttpServletRequest request) {
    User u = (User) session.getAttribute("u");
    if (u != null) {
      User dbUser = entityManager.find(User.class, u.getId());
      dbUser.setMoney(dbUser.getMoney() + 1000.0);
      session.setAttribute("u", dbUser);
    }
    String referer = request.getHeader("Referer");
    return "redirect:" + (referer != null ? referer : "/");
  }

}

