package es.ucm.fdi.iw.controller;

import java.time.LocalDateTime;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import es.ucm.fdi.iw.model.Message;
import es.ucm.fdi.iw.model.Plato;
import es.ucm.fdi.iw.model.User;
import jakarta.persistence.EntityManager;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.transaction.Transactional;

/**
 *  Non-authenticated requests only.
 */
@Controller
public class RootController {

    @Autowired
    private EntityManager entityManager;

    private static final Logger log = LogManager.getLogger(RootController.class);

    @ModelAttribute
    public void populateModel(HttpSession session, Model model) {        
        for (String name : new String[] { "u", "url", "ws", "topics"}) {
          model.addAttribute(name, session.getAttribute(name));
        }
    }

	@GetMapping("/login")
    public String login(Model model, HttpServletRequest request) {
        boolean error = request.getQueryString() != null && request.getQueryString().indexOf("error") != -1;
        model.addAttribute("loginError", error);
        return "login";
    }

	@GetMapping("/")
    public String index(Model model) {
        return "index";
    }

    @GetMapping("/inicio")  //Ruta 
    public String inicio(Model model) {  //nombre de la funcion da igual
        return "inicio";    //nombre de vista
    }

    @GetMapping("/plato")  //Ruta 
    public String plato(Model model) {  //nombre de la funcion da igual

        List<Plato> listaplatos = entityManager.createQuery("SELECT p FROM Plato p", Plato.class).getResultList();
        model.addAttribute("platos", listaplatos);
        return "plato";    //nombre de vista
    }

    @GetMapping("/ranking")  //Ruta 
    public String ranking(Model model) {  //nombre de la funcion da igual
        List<Plato> listaplatos = entityManager.createQuery("SELECT p FROM Plato p", Plato.class).getResultList();
        model.addAttribute("platos", listaplatos);
        return "ranking";    //nombre de vista
    }

    @GetMapping("/contacto")  //Ruta 
    public String contact(Model model) {  //nombre de la funcion da igual
        return "contacto";    //nombre de vista
    }

    @GetMapping("/facultades")  //Ruta 
    public String facu(Model model) {  //nombre de la funcion da igual
        return "facultades";    //nombre de vista
    }

    
    @GetMapping("/carrito")  //Ruta 
    public String carrito(Model model) {  //nombre de la funcion da igual
        return "carrito";    //nombre de vista
    }

    @PostMapping("/contacto/enviar")
    @Transactional // Necesario para guardar en la base de datos
    public String enviarMensaje(@RequestParam String asunto, @RequestParam String mensaje, HttpSession session) {
    
        // 1. Obtenemos el usuario que envía (el que está en sesión)
        User remitente = (User) session.getAttribute("u");
        
        // Si el usuario no está logueado, podrías redirigir a login o manejarlo
        if (remitente == null) {
            return "redirect:/login";
        }

        // 2. Buscar al destinatario (por ejemplo, el administrador con ID 1)
        User admin = entityManager.find(User.class, 1L);

        // 3. Crear y configurar el objeto Message
        Message m = new Message();
        m.setSender(remitente);
        m.setRecipient(admin);
        // Concatenamos el asunto al texto ya que la entidad Message no tiene campo asunto
        m.setText("ASUNTO: " + asunto + " | MENSAJE: " + mensaje);
        m.setDateSent(LocalDateTime.now());

        // 4. Guardar en la base de datos
        entityManager.persist(m);

        // Redirigir con un parámetro de éxito
        return "redirect:/contacto?exito=true";
    }

}
