package es.ucm.fdi.iw.controller;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PathVariable;

import es.ucm.fdi.iw.model.Message;
import es.ucm.fdi.iw.model.User;
import es.ucm.fdi.iw.model.Pedido;
import es.ucm.fdi.iw.model.Consejo;
import jakarta.persistence.EntityManager;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.transaction.Transactional;

/**
 * Controlador principal para las peticiones no autenticadas.
 * Gestiona el acceso público: login, inicio, contacto, etc.
 */
@Controller
public class RootController {

    private static final Logger log = LogManager.getLogger(RootController.class);

    @Autowired
    private EntityManager entityManager;

    /**
     * Añade atributos por defecto al modelo en todas las peticiones a este
     * controlador.
     * Carga variables de sesión necesarias para las vistas, como el usuario
     * logueado ('u').
     */
    @ModelAttribute
    public void populateModel(HttpSession session, Model model) {
        for (String name : new String[] { "u", "url", "ws", "topics" }) {
            model.addAttribute(name, session.getAttribute(name));
        }
    }

    /**
     * Muestra la página de inicio de sesión.
     * Si la URL contiene el parámetro '?error', activa el flag para mostrar el
     * mensaje de error.
     */
    @GetMapping("/login")
    public String login(Model model, HttpServletRequest request) {
        boolean error = request.getQueryString() != null && request.getQueryString().indexOf("error") != -1;
        model.addAttribute("loginError", error);
        return "login";
    }

    private void populateInicioModel(Model model) {
        model.addAttribute("platos", entityManager.createQuery("SELECT p FROM Plato p", es.ucm.fdi.iw.model.Plato.class)
                .setMaxResults(5).getResultList());
        model.addAttribute("facultades",
                entityManager.createQuery("SELECT f FROM Facultad f", es.ucm.fdi.iw.model.Facultad.class)
                        .setMaxResults(5).getResultList());

        // Cargar consejos de la base de datos
        List<Consejo> consejosList = entityManager.createQuery("SELECT c FROM Consejo c", Consejo.class).getResultList();
        List<String> consejosTextos = consejosList.stream()
                .map(Consejo::getTexto)
                .collect(Collectors.toList());
        String jsonConsejos = "[]";
        try {
            jsonConsejos = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(consejosTextos);
        } catch (Exception e) {
            log.error("Error serializando consejos a JSON", e);
        }
        model.addAttribute("consejos", jsonConsejos);
    }

    /**
     * Muestra la página por defecto del sitio (inicio).
     */
    @GetMapping("/")
    public String index(Model model) {
        populateInicioModel(model);
        return "inicio";
    }

    /**
     * Muestra la página principal ("Inicio").
     */
    @GetMapping("/inicio")
    public String inicio(Model model) {
        populateInicioModel(model);
        return "inicio"; // Renderiza 'inicio.html'
    }

    /**
     * Muestra la página de contacto.
     */
    @GetMapping("/contacto")
    public String contact(Model model) {
        return "contacto"; // Renderiza 'contacto.html'
    }

    /**
     * Procesa el envío del formulario de contacto.
     * Crea un mensaje y lo almacena en la base de datos dirigido al administrador.
     */
    @PostMapping("/contacto/enviar")
    @Transactional // Necesario para guardar los cambios en la base de datos
    public String enviarMensaje(@RequestParam String asunto, @RequestParam String mensaje, HttpSession session) {

        // 1. Obtenemos el usuario que envía (el que está en sesión)
        User remitente = (User) session.getAttribute("u");

        // Si el usuario no está logueado, lo redirigimos a la página de login
        if (remitente == null) {
            return "redirect:/login";
        }

        // 2. Buscar al destinatario (en este caso, el administrador general con ID 1)
        User admin = entityManager.find(User.class, 1L);

        // 3. Crear y configurar el objeto Message con los datos del formulario
        Message m = new Message();
        m.setSender(remitente);
        m.setRecipient(admin);
        // Concatenamos el asunto al texto ya que la entidad Message no tiene campo de
        // asunto
        m.setText("ASUNTO: " + asunto + " | MENSAJE: " + mensaje);
        m.setDateSent(LocalDateTime.now());

        // 4. Guardar el mensaje en la base de datos
        entityManager.persist(m);

        // Redirigir de nuevo a la vista de contacto enviando un parámetro de éxito
        // (?exito=true)
        return "redirect:/contacto?exito=true";
    }

    /**
     * Muestra la vista del ticket digital del pedido.
     * Accesible para el dueño del pedido, ADMIN o GESTOR_CAFETERIA.
     */
    @GetMapping("/ticket/{id}")
    public String verTicket(@PathVariable long id, HttpSession session, Model model) {
        User requester = (User) session.getAttribute("u");
        if (requester == null) {
            return "redirect:/login";
        }

        Pedido p = entityManager.find(Pedido.class, id);
        if (p == null) {
            return "redirect:/user/error";
        }

        // Verificación de seguridad
        if (p.getCliente().getId() != requester.getId() 
                && !requester.hasRole(User.Role.ADMIN) 
                && !requester.hasRole(User.Role.GESTOR_CAFETERIA)) {
            throw new UserController.NoEsTuPerfilException(); // Devuelve 403 Forbidden
        }

        model.addAttribute("pedido", p);
        return "ticket"; // Renderiza ticket.html
    }

    /**
     * Pasa el pedido de estado SOLICITADO a FINALIZADO.
     */
    @PostMapping("/ticket/{id}/finalizar")
    @Transactional
    public String finalizarPedido(@PathVariable long id, HttpSession session) {
        User requester = (User) session.getAttribute("u");
        if (requester == null || (!requester.hasRole(User.Role.ADMIN) && !requester.hasRole(User.Role.GESTOR_CAFETERIA))) {
            return "redirect:/login";
        }

        Pedido p = entityManager.find(Pedido.class, id);
        if (p != null && p.getEstado() == Pedido.Estado.SOLICITADO) {
            p.setEstado(Pedido.Estado.FINALIZADO);
            entityManager.merge(p);
            log.info("Pedido {} finalizado por {}", id, requester.getUsername());
        }

        return "redirect:/ticket/" + id;
    }

}
