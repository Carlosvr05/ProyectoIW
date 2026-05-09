package es.ucm.fdi.iw.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import es.ucm.fdi.iw.model.Carrito;
import es.ucm.fdi.iw.model.LineaPedido;
import es.ucm.fdi.iw.model.Pedido;
import es.ucm.fdi.iw.model.Facultad;
import es.ucm.fdi.iw.model.Plato;
import es.ucm.fdi.iw.model.User;
import jakarta.persistence.EntityManager;
import jakarta.servlet.http.HttpSession;
import jakarta.transaction.Transactional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Controller
@RequestMapping("/carrito")
public class CarritoController {

    @Autowired
    private EntityManager entityManager;

    private static final Logger log = LogManager.getLogger(CarritoController.class);

    @ModelAttribute
    public void populateModel(HttpSession session, Model model) {
        for (String name : new String[] { "u", "url", "ws", "topics" }) {
            model.addAttribute(name, session.getAttribute(name));
        }
    }

    // --- CONFIRMAR LA COMPRA (CARRITO -> PEDIDO) ---
    @PostMapping("/comprar")
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

            // 4. Agrupar las líneas de pedido por facultad
            java.util.Map<Facultad, java.util.List<LineaPedido>> porFacultad = new java.util.HashMap<>();
            for (LineaPedido itemCarrito : carrito.getItems()) {
                porFacultad.computeIfAbsent(itemCarrito.getFacultad(), k -> new java.util.ArrayList<>()).add(itemCarrito);
            }

            // Crear un Pedido distinto por cada facultad
            for (java.util.Map.Entry<Facultad, java.util.List<LineaPedido>> entry : porFacultad.entrySet()) {
                Pedido pedido = new Pedido();
                pedido.setCliente(dbUser);
                pedido.setEstado(Pedido.Estado.SOLICITADO);
                entityManager.persist(pedido); // Guardamos para ID

                for (LineaPedido itemCarrito : entry.getValue()) {
                    LineaPedido nuevaLinea = new LineaPedido();
                    nuevaLinea.setPlato(itemCarrito.getPlato());
                    nuevaLinea.setCantidad(itemCarrito.getCantidad());
                    nuevaLinea.setPrecioUnitario(itemCarrito.getPlato().getPrecio());
                    nuevaLinea.setFacultad(itemCarrito.getFacultad());

                    entityManager.persist(nuevaLinea);
                    pedido.getLineas().add(nuevaLinea);
                }
            }

            // 5. Vaciar el carrito
            carrito.getItems().clear();

            // Redirigimos con éxito
            return "redirect:/carrito?comprahecha=true";

        } else {
            // Si no tiene dinero, redirigimos con el aviso
            return "redirect:/carrito?comprahecha=false";
        }
    }

    // --- BORRAR UN PLATO DEL CARRITO ---
    @PostMapping("/quitar/{idLinea}")
    @Transactional
    public String quitarDelCarrito(@PathVariable long idLinea, HttpSession session) {
        User u = (User) session.getAttribute("u");
        if (u == null)
            return "redirect:/login";

        Carrito carrito = entityManager.createQuery("SELECT c FROM Carrito c WHERE c.cliente.id = :uid", Carrito.class)
                .setParameter("uid", u.getId())
                .getSingleResult();

        // Buscamos la línea dentro del carrito y la eliminamos
        carrito.getItems().removeIf(linea -> linea.getId() == idLinea);

        // Al estar en @Transactional, Hibernate actualiza la BD automáticamente
        return "redirect:/carrito";
    }

    @PostMapping("/add/{idPlato}")
    @Transactional
    public String addAlCarrito(
            @PathVariable long idPlato,
            @RequestParam long facultadId, // Recibimos la facultad
            @RequestParam(defaultValue = "1") int cantidad,
            HttpSession session,
            jakarta.servlet.http.HttpServletRequest request) {

        User u = (User) session.getAttribute("u");
        if (u == null) {
            return "redirect:/login";
        }

        User user = entityManager.find(User.class, u.getId());
        Plato plato = entityManager.find(Plato.class, idPlato);
        Facultad facultad = entityManager.find(Facultad.class, facultadId);

        if (plato == null || facultad == null) {
            log.warn("Intento de añadir plato o facultad inexistente: plato={}, facultad={}", idPlato, facultadId);
            return "redirect:/plato";
        }

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

        long targetPlatoId = plato.getId();
        long targetFacultadId = facultad.getId();

        log.info("Añadiendo al carrito: Plato {} de Facultad {}", targetPlatoId, targetFacultadId);

        // Buscamos si ya existe una línea con MISMO plato y MISMA facultad
        LineaPedido existente = carrito.getItems().stream()
                .filter(lp -> lp.getPlato() != null && lp.getPlato().getId() == targetPlatoId)
                .filter(lp -> lp.getFacultad() != null && lp.getFacultad().getId() == targetFacultadId)
                .findFirst()
                .orElse(null);

        if (existente != null) {
            log.info("Se ha encontrado una línea existente (ID {}). Incrementando cantidad en {}", existente.getId(), cantidad);
            existente.setCantidad(existente.getCantidad() + cantidad);
        } else {
            log.info("No existe una línea para esta combinación plato-facultad. Creando nueva LineaPedido.");
            LineaPedido nuevaLinea = new LineaPedido();
            nuevaLinea.setPlato(plato);
            nuevaLinea.setFacultad(facultad); 
            nuevaLinea.setCantidad(cantidad);
            nuevaLinea.setPrecioUnitario(plato.getPrecio());

            carrito.getItems().add(nuevaLinea);
        }

        String referer = request.getHeader("Referer");
        return "redirect:" + (referer != null ? referer : "/plato");
    }

    @GetMapping
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

}
