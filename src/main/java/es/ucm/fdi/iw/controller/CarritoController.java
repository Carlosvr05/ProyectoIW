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
import org.springframework.messaging.simp.SimpMessagingTemplate;

/**
 * Controlador que gestiona la lógica del Carrito de la Compra virtual.
 * Permite añadir platos, eliminarlos, ver el estado actual de la cesta,
 * y confirmar pedidos dividiéndolos por facultad.
 */
@Controller
@RequestMapping("/carrito")
public class CarritoController {

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    private static final Logger log = LogManager.getLogger(CarritoController.class);

    /**
     * Inyecta variables comunes de sesión en el modelo de todas las peticiones a '/carrito/*'.
     */
    @ModelAttribute
    public void populateModel(HttpSession session, Model model) {
        for (String name : new String[] { "u", "url", "ws", "topics" }) {
            model.addAttribute(name, session.getAttribute(name));
        }
    }

    /**
     * CONFIRMAR COMPRA: Transforma el carrito virtual en pedidos reales.
     * Cobra al usuario y genera pedidos diferentes por cada Facultad de recogida involucrada.
     */
    @PostMapping("/comprar")
    @Transactional
    public String confirmarCompra(HttpSession session) {

        User sessionUser = (User) session.getAttribute("u");
        // Prevención de acceso sin autenticar
        if (sessionUser == null) {
            return "redirect:/login";
        }

        // 1. Recuperar datos frescos de la BD para el usuario y su carrito asociado
        User dbUser = entityManager.find(User.class, sessionUser.getId());
        Carrito carrito = entityManager.createQuery("SELECT c FROM Carrito c WHERE c.cliente.id = :uid", Carrito.class)
                .setParameter("uid", dbUser.getId())
                .getSingleResult();

        Double totalcarro = carrito.getTotal();
        Double dinero = dbUser.getMoney(); // Saldo actual de la cartera del usuario

        // 2. Validación de saldo suficiente
        if (totalcarro <= dinero) {

            // 3. Efectuar el cobro (BD) y sincronizar la copia de sesión
            dbUser.setMoney(dinero - totalcarro);
            session.setAttribute("u", dbUser);

            // 4. Lógica de separación de pedidos:
            // Agrupar las líneas del carrito según a qué Facultad pertenezcan
            java.util.Map<Facultad, java.util.List<LineaPedido>> porFacultad = new java.util.HashMap<>();
            for (LineaPedido itemCarrito : carrito.getItems()) {
                porFacultad.computeIfAbsent(itemCarrito.getFacultad(), k -> new java.util.ArrayList<>()).add(itemCarrito);
            }

            // Crear un objeto Pedido distinto en la BD por cada facultad encontrada
            for (java.util.Map.Entry<Facultad, java.util.List<LineaPedido>> entry : porFacultad.entrySet()) {
                Pedido pedido = new Pedido();
                pedido.setCliente(dbUser);
                pedido.setEstado(Pedido.Estado.SOLICITADO);
                entityManager.persist(pedido); // Persistir primero para generar un ID válido

                // Transferir las líneas (platos + cantidad) del carrito al pedido real
                for (LineaPedido itemCarrito : entry.getValue()) {
                    LineaPedido nuevaLinea = new LineaPedido();
                    nuevaLinea.setPlato(itemCarrito.getPlato());
                    nuevaLinea.setCantidad(itemCarrito.getCantidad());
                    nuevaLinea.setPrecioUnitario(itemCarrito.getPlato().getPrecio()); // Foto del precio actual
                    nuevaLinea.setFacultad(itemCarrito.getFacultad());

                    entityManager.persist(nuevaLinea);
                    pedido.getLineas().add(nuevaLinea);
                }

                // Sincronizar cambios para obtener el ID real generado
                entityManager.flush();

                // Construimos la estructura JSON de la comanda para el panel de cocina
                StringBuilder platosJson = new StringBuilder("[");
                for (int i = 0; i < pedido.getLineas().size(); i++) {
                    LineaPedido lp = pedido.getLineas().get(i);
                    platosJson.append("{")
                            .append("\"platoNombre\":\"").append(lp.getPlato().getNombre()).append("\",")
                            .append("\"cantidad\":").append(lp.getCantidad())
                            .append("}");
                    if (i < pedido.getLineas().size() - 1) {
                        platosJson.append(",");
                    }
                }
                platosJson.append("]");

                String horaCompra = pedido.getFechaCompra() != null 
                        ? pedido.getFechaCompra().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))
                        : java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"));

                String jsonCocina = "{"
                        + "\"id\": " + pedido.getId() + ","
                        + "\"facultadId\": " + entry.getKey().getId() + ","
                        + "\"estado\": \"" + pedido.getEstado().name() + "\","
                        + "\"cliente\": \"" + dbUser.getUsername() + "\","
                        + "\"hora\": \"" + horaCompra + "\","
                        + "\"items\": " + platosJson.toString()
                        + "}";

                // Notificar masivamente al monitor de cocina asíncronamente por WebSocket!
                messagingTemplate.convertAndSend("/topic/pedidos-cocina", jsonCocina);
            }

            // 5. El carrito se vacía tras finalizar los pedidos
            carrito.getItems().clear();

            // Redirigir a la vista de carrito mostrando el mensaje de éxito
            return "redirect:/carrito?comprahecha=true";

        } else {
            // El usuario no tiene dinero suficiente, redirigimos mostrando advertencia
            return "redirect:/carrito?comprahecha=false";
        }
    }

    /**
     * BORRAR PLATO: Elimina una línea concreta del carrito activo del usuario.
     */
    @PostMapping("/quitar/{idLinea}")
    @Transactional
    public String quitarDelCarrito(@PathVariable long idLinea, HttpSession session) {
        User u = (User) session.getAttribute("u");
        if (u == null)
            return "redirect:/login";

        // Obtener el carrito actual
        Carrito carrito = entityManager.createQuery("SELECT c FROM Carrito c WHERE c.cliente.id = :uid", Carrito.class)
                .setParameter("uid", u.getId())
                .getSingleResult();

        // Localizar la línea por su ID y removerla del carrito
        carrito.getItems().removeIf(linea -> linea.getId() == idLinea);

        // Al ejecutarse en un entorno @Transactional, Hibernate elimina la línea huérfana
        return "redirect:/carrito";
    }

    /**
     * AÑADIR PLATO: Agrega un plato y cantidad de una facultad específica al carrito virtual.
     */
    @PostMapping("/add/{idPlato}")
    @Transactional
    public String addAlCarrito(
            @PathVariable long idPlato,
            @RequestParam long facultadId, // Es vital saber de dónde se va a recoger
            @RequestParam(defaultValue = "1") int cantidad,
            HttpSession session,
            jakarta.servlet.http.HttpServletRequest request) {

        User u = (User) session.getAttribute("u");
        if (u == null) {
            return "redirect:/login";
        }

        // Cargar las entidades persistentes
        User user = entityManager.find(User.class, u.getId());
        Plato plato = entityManager.find(Plato.class, idPlato);
        Facultad facultad = entityManager.find(Facultad.class, facultadId);

        // Validar que el plato y la facultad existen
        if (plato == null || facultad == null) {
            log.warn("Intento de añadir plato o facultad inexistente: plato={}, facultad={}", idPlato, facultadId);
            return "redirect:/plato"; // Abortamos
        }

        Carrito carrito;
        try {
            // Buscamos si el usuario ya tiene un carrito guardado
            carrito = entityManager.createQuery("SELECT c FROM Carrito c WHERE c.cliente.id = :uid", Carrito.class)
                    .setParameter("uid", user.getId())
                    .getSingleResult();
        } catch (jakarta.persistence.NoResultException e) {
            // Si es su primera vez comprando, se le asigna un carrito vacío y se persiste
            carrito = new Carrito();
            carrito.setCliente(user);
            entityManager.persist(carrito);
        }

        long targetPlatoId = plato.getId();
        long targetFacultadId = facultad.getId();

        log.info("Añadiendo al carrito: Plato {} de Facultad {}", targetPlatoId, targetFacultadId);

        // Buscar si en el carrito YA hay una línea con EXACTAMENTE el mismo plato y la misma facultad
        LineaPedido existente = carrito.getItems().stream()
                .filter(lp -> lp.getPlato() != null && lp.getPlato().getId() == targetPlatoId)
                .filter(lp -> lp.getFacultad() != null && lp.getFacultad().getId() == targetFacultadId)
                .findFirst()
                .orElse(null);

        if (existente != null) {
            // Si existe, simplemente incrementamos la cantidad deseada
            log.info("Se ha encontrado una línea existente (ID {}). Incrementando cantidad en {}", existente.getId(), cantidad);
            existente.setCantidad(existente.getCantidad() + cantidad);
        } else {
            // Si no existe esa combinación, creamos una nueva línea en la lista
            log.info("No existe una línea para esta combinación plato-facultad. Creando nueva LineaPedido.");
            LineaPedido nuevaLinea = new LineaPedido();
            nuevaLinea.setPlato(plato);
            nuevaLinea.setFacultad(facultad); 
            nuevaLinea.setCantidad(cantidad);
            nuevaLinea.setPrecioUnitario(plato.getPrecio());

            carrito.getItems().add(nuevaLinea);
        }

        // Devolver al usuario a la página desde donde clicó en "Añadir"
        String referer = request.getHeader("Referer");
        return "redirect:" + (referer != null ? referer : "/plato");
    }

    /**
     * VISTA DEL CARRITO: Renderiza la página web 'carrito.html' pasando el objeto carrito actual.
     */
    @GetMapping
    @Transactional // A veces es necesario para inicializar colecciones lazy vinculadas a carrito
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
            // Si el carrito no existe en la BD, la variable 'carrito' queda a nulo.
            // Thymeleaf (html) ya está preparado para mostrar el mensaje de "Tu carrito está vacío"
        }

        // Inyectamos el carrito en el modelo
        model.addAttribute("carrito", carrito);
        return "carrito";
    }

}
