package es.ucm.fdi.iw.controller;

import java.io.File;
import java.io.FileNotFoundException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.ui.Model;
import org.springframework.util.ResourceUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import es.ucm.fdi.iw.model.Topic;
import es.ucm.fdi.iw.model.Message;
import es.ucm.fdi.iw.model.User;
import es.ucm.fdi.iw.model.User.Role;
import io.karatelabs.js.Context;
import io.karatelabs.js.Interpreter;
import io.karatelabs.js.Node;
import io.karatelabs.js.Parser;
import io.karatelabs.js.Source;
import jakarta.persistence.EntityManager;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.transaction.Transactional;

/**
 * Controlador API (REST) orientado principalmente a usuarios autenticados.
 *
 * Devuelve directamente JSON en lugar de vistas HTML (gracias
 * a @RestController).
 * El acceso a los endpoints no está restringido globalmente aquí, debe hacerse
 * mediante la configuración de seguridad o métodos de validación internos.
 */
@RestController
@RequestMapping("api")
public class ApiController {

  @Autowired
  private EntityManager entityManager;

  private static final Logger log = LogManager.getLogger(ApiController.class);

  /**
   * Endpoint de prueba simple - devuelve el mensaje que se le pase por la URL.
   * Útil para comprobar si el servidor está en línea y procesando peticiones.
   * 
   * @param message El mensaje recibido en la URL
   * @return {"coder": "mensaje"} en formato JSON
   */
  @GetMapping("/status/{message}")
  public Map<String, String> check(@PathVariable String message) {
    return Map.of("coder", message);
  }

  /**
   * Cuenta el número total de usuarios registrados en el sistema.
   * 
   * @return Un mapa JSON {"count": X} con la cantidad de usuarios
   */
  @GetMapping("/users/count")
  public Map<String, Long> usersCount() {
    return Map.of("count",
        (Long) entityManager.createQuery("SELECT COUNT(u) FROM User u").getSingleResult());
  }

  /**
   * Carga un archivo desde el classpath del servidor.
   * Especialmente útil porque funciona incluso cuando la aplicación está
   * empaquetada en un JAR.
   * 
   * @param path - Ruta al archivo relativa a `target/classes` o
   *             `src/main/resources`
   * @return El archivo encontrado
   * @throws RuntimeException si el archivo no existe
   */
  private File loadFromClasspath(String path) {
    try {
      return ResourceUtils.getFile("classpath:" + path);
    } catch (FileNotFoundException e) {
      throw new RuntimeException("Could not load file from classpath: " + path, e);
    }
  }

  /**
   * Ejecuta código Javascript interpretado (usando karate-js) desde Java.
   * 
   * @param source Código fuente JS a evaluar
   * @param vars   Variables iniciales pasadas al contexto del script
   * @return El resultado de la evaluación JS
   */
  private Object eval(String source, Map<String, Object> vars) {
    Parser parser = new Parser(new Source(source));
    Node node = parser.parse();
    Context context = Context.root();
    if (vars != null) {
      vars.forEach((k, v) -> context.declare(k, v));
    }
    return Interpreter.eval(node, context);
  }

  /**
   * Endpoint de prueba que ejecuta código JS ubicado en un fichero interno
   * y devuelve el resultado.
   */
  @GetMapping(value = "/js", produces = MediaType.APPLICATION_JSON_VALUE)
  public Map<String, String> testJs() throws Exception {
    String start = Files.readString(
        loadFromClasspath("static/js/js-eval.js").toPath());
    String source = start + "\n" + "f(v);";

    Object result = eval(source, Map.of(
        "v", 10,
        "exampleExternalVar", "patata"));
    return Map.of("result", result.toString());
  }

  @Autowired
  private SimpMessagingTemplate messagingTemplate;

  /**
   * Publica y guarda un nuevo mensaje en un "topic" (grupo de chat).
   * 
   * @param name Nombre/clave del topic de destino
   * @param o    Mensaje JSON (ej: {"message": "hola mundo"})
   */
  @PostMapping("/topic/{name}")
  @ResponseBody
  @Transactional
  public Map<String, String> postMsg(@PathVariable String name,
      @RequestBody JsonNode o, Model model, HttpSession session,
      HttpServletResponse response)
      throws JsonProcessingException {

    String text = o.get("message").asText();
    // Identificar al remitente a través de la sesión
    User sender = entityManager.find(
        User.class, ((User) session.getAttribute("u")).getId());
    // Identificar el grupo de chat destino
    Topic target = entityManager.createNamedQuery("Topic.byKey", Topic.class)
        .setParameter("key", name).getSingleResult();

    // Verificar permisos: Sólo un Admin o un miembro del grupo puede escribir
    if (!sender.hasRole(Role.ADMIN) && !target.getMembers().contains(sender)) {
      response.setStatus(HttpServletResponse.SC_FORBIDDEN);
      return Map.of("error", "user not in group");
    }

    // Crear el mensaje en BD
    Message m = new Message();
    m.setRecipient(null); // Es un mensaje grupal, no personal
    m.setSender(sender);
    m.setTopic(target);
    m.setDateSent(LocalDateTime.now());
    m.setText(text);
    entityManager.persist(m);
    entityManager.flush(); // Forzamos el flush para obtener el ID del mensaje

    // Transformarlo a JSON ligero y enviarlo por WebSockets
    String json = new ObjectMapper().writeValueAsString(m.toTransfer());
    log.info("Sending a message to  group {} with contents '{}'", target.getName(), json);
    messagingTemplate.convertAndSend("/topic/" + name, json);
    return Map.of("result", "message sent");
  }

  /**
   * Recupera el historial completo de mensajes de un "topic" (grupo de chat).
   * 
   * @param name Clave identificadora del grupo
   */
  @GetMapping("/topic/{name}")
  @ResponseBody
  @Transactional
  public Map<String, String> getMessages(@PathVariable String name, HttpSession session,
      HttpServletResponse response)
      throws JsonProcessingException {

    // Usuario que solicita los mensajes
    User requester = entityManager.find(
        User.class, ((User) session.getAttribute("u")).getId());
    Topic target = entityManager.createNamedQuery("Topic.byKey", Topic.class)
        .setParameter("key", name).getSingleResult();

    // Verificamos permisos de lectura (Admin o miembro)
    if (!requester.hasRole(Role.ADMIN) && !target.getMembers().contains(requester)) {
      response.setStatus(HttpServletResponse.SC_FORBIDDEN);
      return Map.of("error", "user not in group");
    }

    // Transformamos los mensajes de base de datos a un array JSON de Transferables
    return Map.of("messages", new ObjectMapper().writeValueAsString(
        target.getMessages().stream()
            .map(Message::toTransfer).toArray()));
  }

  /**
   * Recupera la valoración media de un plato en una facultad concreta,
   * incluyendo si el usuario actual ya ha votado.
   */
  @GetMapping("/plato/{platoId}/facultad/{facultadId}/rating")
  @ResponseBody
  public Map<String, Object> getRating(@PathVariable long platoId, @PathVariable long facultadId, HttpSession session) {
    // 1. Obtener todas las valoraciones para ese plato en esa facultad
    java.util.List<es.ucm.fdi.iw.model.Valoracion> valoraciones = entityManager.createQuery(
        "SELECT v FROM Valoracion v WHERE v.plato.id = :pid AND v.facultad.id = :fid",
        es.ucm.fdi.iw.model.Valoracion.class)
        .setParameter("pid", platoId)
        .setParameter("fid", facultadId)
        .getResultList();

    // 2. Calcular la media matemática de estrellas
    double sum = 0;
    for (es.ucm.fdi.iw.model.Valoracion v : valoraciones) {
      sum += v.getPuntuacion();
    }
    double average = valoraciones.isEmpty() ? 0.0 : sum / valoraciones.size();

    // 3. Comprobar si el usuario en sesión ya había votado antes
    User u = (User) session.getAttribute("u");
    Integer userVote = null;
    if (u != null) {
      for (es.ucm.fdi.iw.model.Valoracion v : valoraciones) {
        if (v.getUser().getId() == u.getId()) {
          userVote = v.getPuntuacion();
          break;
        }
      }
    }

    // 4. Preparar la respuesta JSON
    java.util.Map<String, Object> response = new java.util.HashMap<>();
    response.put("average", Math.round(average * 10.0) / 10.0); // Redondeo a 1 decimal
    response.put("count", valoraciones.size());
    response.put("userVote", userVote == null ? -1 : userVote); // -1 significa no ha votado
    return response;
  }

  /**
   * Registra un nuevo voto (estrellas) de un usuario para un plato específico
   * que se vende en una facultad específica.
   */
  @PostMapping("/plato/{platoId}/facultad/{facultadId}/vote")
  @ResponseBody
  @Transactional // Altera la BD
  public Map<String, Object> submitVote(@PathVariable long platoId, @PathVariable long facultadId,
      @RequestBody Map<String, Integer> payload, HttpSession session) {
    User u = (User) session.getAttribute("u");
    // Filtro manual de autenticación
    if (u == null) {
      return java.util.Map.of("error", "No autenticado");
    }

    int puntuacion = payload.get("puntuacion");

    if (puntuacion < 1 || puntuacion > 5) {
      return java.util.Map.of("error", "Puntuación inválida. Debe estar entre 1 y 5.");
    }

    // Comprobar si ya votó previamente (evitar votos duplicados del mismo usuario)
    java.util.List<es.ucm.fdi.iw.model.Valoracion> existentes = entityManager.createQuery(
        "SELECT v FROM Valoracion v WHERE v.plato.id = :pid AND v.facultad.id = :fid AND v.user.id = :uid",
        es.ucm.fdi.iw.model.Valoracion.class)
        .setParameter("pid", platoId)
        .setParameter("fid", facultadId)
        .setParameter("uid", u.getId())
        .getResultList();

    if (!existentes.isEmpty()) {
      return java.util.Map.of("error", "Ya has votado"); // Rechazado
    }

    // Si no ha votado, persistimos su nueva valoración
    es.ucm.fdi.iw.model.Valoracion nuevaVal = new es.ucm.fdi.iw.model.Valoracion();
    nuevaVal.setUser(entityManager.find(User.class, u.getId()));
    nuevaVal.setPlato(entityManager.find(es.ucm.fdi.iw.model.Plato.class, platoId));
    nuevaVal.setFacultad(entityManager.find(es.ucm.fdi.iw.model.Facultad.class, facultadId));
    nuevaVal.setPuntuacion(puntuacion);

    entityManager.persist(nuevaVal);

    return java.util.Map.of("success", true);
  }
}
