package es.ucm.fdi.iw.controller;

import java.time.LocalDateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import es.ucm.fdi.iw.model.Message;
import es.ucm.fdi.iw.model.User;
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

    @Autowired
    private EntityManager entityManager;

    /**
     * Añade atributos por defecto al modelo en todas las peticiones a este controlador.
     * Carga variables de sesión necesarias para las vistas, como el usuario logueado ('u').
     */
    @ModelAttribute
    public void populateModel(HttpSession session, Model model) {
        for (String name : new String[] { "u", "url", "ws", "topics" }) {
            model.addAttribute(name, session.getAttribute(name));
        }
    }

    /**
     * Muestra la página de inicio de sesión.
     * Si la URL contiene el parámetro '?error', activa el flag para mostrar el mensaje de error.
     */
    @GetMapping("/login")
    public String login(Model model, HttpServletRequest request) {
        boolean error = request.getQueryString() != null && request.getQueryString().indexOf("error") != -1;
        model.addAttribute("loginError", error);
        return "login";
    }

    /**
     * Muestra la página por defecto del sitio (Index).
     */
    @GetMapping("/")
    public String index(Model model) {
        return "index";
    }

    /**
     * Muestra la página principal ("Inicio").
     */
    @GetMapping("/inicio")
    public String inicio(Model model) {
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
        // Concatenamos el asunto al texto ya que la entidad Message no tiene campo de asunto
        m.setText("ASUNTO: " + asunto + " | MENSAJE: " + mensaje);
        m.setDateSent(LocalDateTime.now());

        // 4. Guardar el mensaje en la base de datos
        entityManager.persist(m);

        // Redirigir de nuevo a la vista de contacto enviando un parámetro de éxito (?exito=true)
        return "redirect:/contacto?exito=true";
    }

}
