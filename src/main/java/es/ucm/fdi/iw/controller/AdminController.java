package es.ucm.fdi.iw.controller;

import java.util.List;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import es.ucm.fdi.iw.model.Topic;
import es.ucm.fdi.iw.model.Lorem;
import es.ucm.fdi.iw.model.Message;
import es.ucm.fdi.iw.model.Transferable;
import es.ucm.fdi.iw.model.User;
import es.ucm.fdi.iw.model.Consejo;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import jakarta.servlet.http.HttpSession;
import jakarta.transaction.Transactional;

/**
 * Controlador de Administración del sitio.
 * 
 * Gestiona el panel de administrador, listado de usuarios, y estado del
 * sistema.
 * El acceso a todas las rutas bajo "/admin" está protegido y requiere
 * autenticación
 * (ver configuración en SecurityConfig).
 */
@Controller
@RequestMapping("admin")
public class AdminController {

  @Autowired
  private PasswordEncoder passwordEncoder;

  @Autowired
  private EntityManager entityManager;

  private static final Logger log = LogManager.getLogger(AdminController.class);

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
   * Muestra el panel principal de administración.
   * Carga todos los usuarios de la base de datos para mostrarlos en la tabla.
   */
  @GetMapping("/")
  public String index(Model model) {
    log.info("Admin acaba de entrar");
    model.addAttribute("users",
        entityManager.createQuery("select u from User u").getResultList());
    return "admin"; // Renderiza admin.html
  }

  /**
   * Cambia asíncronamente el estado de habilitado/deshabilitado de un usuario.
   * Usado por los botones de Banear/Habilitar del panel de admin sin recargar la
   * página.
   */
  @PostMapping("/toggle/{id}")
  @Transactional // Necesario porque modifica un estado en la BD
  @ResponseBody // Devuelve datos JSON, no una vista HTML
  public String toggleUser(@PathVariable long id, Model model) {
    log.info("Admin cambia estado de " + id);
    User target = entityManager.find(User.class, id);
    target.setEnabled(!target.isEnabled()); // Invierte su estado (Activo/Inactivo)
    return "{\"enabled\":" + target.isEnabled() + "}";
  }

  /**
   * Devuelve los últimos mensajes enviados por el sistema en formato JSON.
   * Usado por DataTable en admin.html para poblar el log.
   */
  @GetMapping(path = "all-messages", produces = "application/json")
  @Transactional // Para no recibir resultados inconsistentes a medio escribir
  @ResponseBody // Indica que devuelve un objeto (JSON), no una vista
  public List<Message.Transfer> retrieveMessages(HttpSession session) {
    TypedQuery<Message> query = entityManager.createQuery("select m from Message m", Message.class);
    query.setMaxResults(5); // Limita los resultados a los últimos 5 mensajes
    query.setFirstResult(0); // Útil para paginar (indica a partir de qué fila extraer)

    // Devuelve los mensajes transformados en objetos ligeros de transferencia (DTO)
    return query.getResultList().stream()
        .map(Transferable::toTransfer)
        .collect(Collectors.toList());
  }

  /**
   * Endpoint de desarrollo para rellenar la base de datos con datos de prueba
   * (usuarios aleatorios y grupos).
   */
  @RequestMapping("/populate")
  @ResponseBody
  @Transactional // Todas las inserciones se harán bajo una misma transacción
  public String populate(Model model) {

    // Crear un par de grupos de mensajería
    Topic g1 = new Topic();
    g1.setName("g1");
    g1.setKey(UserController.generateRandomBase64Token(6));
    entityManager.persist(g1);

    Topic g2 = new Topic();
    g2.setName("g2");
    g2.setKey(UserController.generateRandomBase64Token(6));
    entityManager.persist(g2);

    // Crear 15 usuarios con datos aleatorios y asignarlos a los grupos
    for (int i = 0; i < 15; i++) {
      User u = new User();
      u.setUsername("user" + i);
      u.setPassword(passwordEncoder.encode("aa")); // Contraseña genérica 'aa'
      u.setEnabled(true);
      u.setRoles(User.Role.USER.toString());
      u.setFirstName(Lorem.nombreAlAzar());
      u.setLastName(Lorem.apellidoAlAzar());
      entityManager.persist(u);

      // Asignar a grupos alternamente para dar variedad
      if (i % 2 == 0) {
        g1.getMembers().add(u);
      }
      if (i % 3 == 0) {
        g2.getMembers().add(u);
      }
    }
    return "{\"admin\": \"populated\"}"; // Responde éxito en formato JSON
  }

  /**
   * Muestra la vista de gestión de consejos para el administrador.
   */
  @GetMapping("/consejos")
  public String manageConsejos(Model model) {
      log.info("Admin accede a la gestión de consejos");
      List<Consejo> consejos = entityManager.createQuery("SELECT c FROM Consejo c", Consejo.class)
              .getResultList();
      model.addAttribute("consejos", consejos);
      return "gestor_consejos";
  }

  /**
   * Crea un nuevo consejo saludable.
   */
  @PostMapping("/consejos/add")
  @Transactional
  public String addConsejo(@RequestParam String texto) {
      Consejo c = new Consejo();
      c.setTexto(texto);
      entityManager.persist(c);
      log.info("Consejo añadido correctamente: {}", texto);
      return "redirect:/admin/consejos";
  }

  /**
   * Edita un consejo existente en el sistema.
   */
  @PostMapping("/consejos/edit/{id}")
  @Transactional
  public String editConsejo(@PathVariable long id, @RequestParam String texto) {
      Consejo c = entityManager.find(Consejo.class, id);
      if (c != null) {
          c.setTexto(texto);
          entityManager.merge(c);
          log.info("Consejo {} editado correctamente", id);
      }
      return "redirect:/admin/consejos";
  }

  /**
   * Elimina un consejo por completo.
   */
  @PostMapping("/consejos/delete/{id}")
  @Transactional
  public String deleteConsejo(@PathVariable long id) {
      Consejo c = entityManager.find(Consejo.class, id);
      if (c != null) {
          entityManager.remove(c);
          log.info("Consejo {} eliminado", id);
      }
      return "redirect:/admin/consejos";
  }

}
