package es.ucm.fdi.iw.controller;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import es.ucm.fdi.iw.model.Carrito;
import es.ucm.fdi.iw.model.Facultad;
import es.ucm.fdi.iw.model.LineaPedido;
import es.ucm.fdi.iw.model.Message;
import es.ucm.fdi.iw.model.Pedido;
import es.ucm.fdi.iw.model.Plato;
import es.ucm.fdi.iw.model.User;
import jakarta.persistence.EntityManager;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.transaction.Transactional;
import org.springframework.web.bind.annotation.RequestBody;


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
    @Transactional
    public String carrito(Model model, HttpSession session) {
        User u = (User) session.getAttribute("u");
        if (u == null) {
            return "redirect:/login";
        }

        Carrito carrito = null;
        try {
            carrito = entityManager.createQuery("SELECT c FROM Carrito c WHERE c.cliente.id = :uid", Carrito.class)
                    .setParameter("uid", u.getId())
                    .getSingleResult();
        } catch (jakarta.persistence.NoResultException e) {
            // Si no hay carrito, pasamos null o no pasamos nada y lo controlamos en el HTML
        }

        model.addAttribute("carrito", carrito);
        return "carrito";
    }

    @PostMapping("/carrito/add/{idPlato}")
    @Transactional
    public String addAlCarrito(
            @PathVariable long idPlato, 
            @RequestParam(defaultValue = "1") int cantidad, // ¡Añadimos esto para recibir el número!
            HttpSession session) {
            
        User u = (User) session.getAttribute("u");
        if (u == null) {
            return "redirect:/login"; 
        }

        User user = entityManager.find(User.class, u.getId());
        Plato plato = entityManager.find(Plato.class, idPlato);

        Carrito carrito;
        try {
            carrito = entityManager.createQuery("SELECT c FROM Carrito c WHERE c.cliente.id = :uid", Carrito.class)
                    .setParameter("uid", user.getId())
                    .getSingleResult();
        } catch (jakarta.persistence.NoResultException e) {
            carrito = new Carrito();
            carrito.setCliente(user);
            entityManager.persist(carrito);
        }

        boolean encontrado = false;
        for (LineaPedido lp : carrito.getItems()) {
            if (lp.getPlato().getId() == plato.getId()) {
                // Si ya estaba en el carrito, le sumamos la NUEVA cantidad a la que ya había
                lp.setCantidad(lp.getCantidad() + cantidad);
                encontrado = true;
                break;
            }
        }

        if (!encontrado) {
            LineaPedido nuevaLinea = new LineaPedido();
            nuevaLinea.setPlato(plato);
            // Guardamos la cantidad que ha elegido el usuario en la web
            nuevaLinea.setCantidad(cantidad); 
            nuevaLinea.setPrecioUnitario(plato.getPrecio());
            
            carrito.getItems().add(nuevaLinea); 
        }

        return "redirect:/carrito";
    }

    // --- BORRAR UN PLATO DEL CARRITO ---
    @PostMapping("/carrito/quitar/{idLinea}")
    @Transactional
    public String quitarDelCarrito(@PathVariable long idLinea, HttpSession session) {
        User u = (User) session.getAttribute("u");
        if (u == null) return "redirect:/login";

        Carrito carrito = entityManager.createQuery("SELECT c FROM Carrito c WHERE c.cliente.id = :uid", Carrito.class)
                .setParameter("uid", u.getId())
                .getSingleResult();

        // Buscamos la línea dentro del carrito y la eliminamos
        carrito.getItems().removeIf(linea -> linea.getId() == idLinea);
        
        // Al estar en @Transactional, Hibernate actualiza la BD automáticamente
        return "redirect:/carrito";
    }

    // --- CONFIRMAR LA COMPRA (CARRITO -> PEDIDO) ---
   @PostMapping("/carrito/comprar")
    @Transactional
    public String confirmarCompra(HttpSession session) {
        
        User sessionUser = (User) session.getAttribute("u");
        if (sessionUser == null) {
            return "redirect:/login";
        }

        // 1. Recuperar el usuario real y el carrito
        User dbUser = entityManager.find(User.class, sessionUser.getId());
        Carrito carrito = entityManager.createQuery("SELECT c FROM Carrito c WHERE c.cliente.id = :uid", Carrito.class)
                .setParameter("uid", dbUser.getId())
                .getSingleResult();

        Double totalcarro = carrito.getTotal();
        Double dinero = dbUser.getMoney();

        // 2. Comprobar si tiene dinero suficiente
        if (totalcarro <= dinero) {
            
            // 3. Cobrar al usuario y actualizar la sesión
            dbUser.setMoney(dinero - totalcarro);
            session.setAttribute("u", dbUser);

            // 4. Crear el Pedido a partir de los datos del carrito
            Pedido pedido = new Pedido();
            pedido.setCliente(dbUser);
            pedido.setEstado(Pedido.Estado.SOLICITADO); 
            entityManager.persist(pedido); // Guardamos el pedido primero para que tenga ID

            // 4.1. Creamos esas lineas de pedido y las metemos en un pedido nuevo para que no de error al intentar crear un nuevo pedido
            for (LineaPedido itemCarrito : carrito.getItems()) {
                LineaPedido nuevaLinea = new LineaPedido();
                nuevaLinea.setPlato(itemCarrito.getPlato());
                nuevaLinea.setCantidad(itemCarrito.getCantidad());
                // Guardamos el precio al que lo ha comprado (por si en el futuro el admin lo cambia)
                nuevaLinea.setPrecioUnitario(itemCarrito.getPlato().getPrecio()); 
                
                entityManager.persist(nuevaLinea); // Guardamos la línea en la BD
                pedido.getLineas().add(nuevaLinea); // Se la adjuntamos al pedido definitivo
            }

            // 5. Vaciar el carrito (ahora sí, las líneas viejas se pueden borrar sin problema)
            carrito.getItems().clear(); 

            // Redirigimos con éxito
            return "redirect:/carrito?comprahecha=true"; 

        } else {
            // Si no tiene dinero, redirigimos con el aviso
            return "redirect:/carrito?comprahecha=false";
        }
    }

    @PostMapping("/contacto/enviar")
    @Transactional // Necesario para guardar en la base de datos
    public String enviarMensaje(@RequestParam String asunto, @RequestParam String mensaje, HttpSession session) {
    
        // 1. Obtenemos el usuario que envía (el que está en sesión)
        User remitente = (User) session.getAttribute("u");
        
        // Si el usuario no está logueado, lo redirigimos a login
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


    @GetMapping("/gestor")
    public String managePlatos(Model model) {
      log.info("Admin accede a la gestión de platos");
      // Obtenemos todos los platos de la base de datos
      List<Plato> platos = entityManager.createQuery("select p from Plato p", Plato.class).getResultList();
      // Obtenemos también las facultades para mostrarlas en el formulario
      List<Facultad> facultades = entityManager.createQuery("select f from Facultad f", Facultad.class).getResultList();
      model.addAttribute("platos", platos);
      model.addAttribute("facultades", facultades);
      return "gestor"; 
    }

    @PostMapping("/gestor/addPlato")
    @Transactional
        public String addPlato(
            @RequestParam String nombre,
            @RequestParam String descripcion,
            @RequestParam double precio,
            @RequestParam String imagen,
            @RequestParam(required = false) List<Long> facultadIds) {
            
        
            Plato p = new Plato();
            p.setNombre(nombre);
            p.setDescripcion(descripcion);
            p.setPrecio(precio);
            p.setImagen(imagen);
            p.setActivo(true); // Asumimos que al crearlo está activo por defecto
            
            if (facultadIds != null && !facultadIds.isEmpty()) {
                Set<Facultad> facultadesAsignadas = new HashSet<>();
                for (Long id : facultadIds) {
                    Facultad f = entityManager.find(Facultad.class, id);
                    if (f != null) {
                        facultadesAsignadas.add(f);
                    }
                }
                p.setFacultades(facultadesAsignadas);
            }
            entityManager.persist(p);
            
            // Redirigimos de vuelta a la página de platos
            return "redirect:/gestor";
    }

    @PostMapping("/gestor/deletePlato/{id}")
    @Transactional
    public String deletePlato(@PathVariable long id) {
        Plato p = entityManager.find(Plato.class, id);
        if (p != null) {
            // Nota: Si el plato está en la tabla plato_facultades, puede que necesites
            // vaciar su lista de facultades antes de borrarlo para evitar errores de clave foránea.
            p.getFacultades().clear(); 
            entityManager.remove(p);
        }
        return "redirect:/gestor";
    }

    
    

}
