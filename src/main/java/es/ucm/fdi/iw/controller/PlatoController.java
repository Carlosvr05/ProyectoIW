package es.ucm.fdi.iw.controller;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.FileCopyUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import es.ucm.fdi.iw.LocalData;
import es.ucm.fdi.iw.model.Facultad;
import es.ucm.fdi.iw.model.Plato;
import es.ucm.fdi.iw.model.User;
import es.ucm.fdi.iw.model.Pedido;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.ResponseBody;
import jakarta.persistence.EntityManager;
import jakarta.servlet.http.HttpSession;
import jakarta.transaction.Transactional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Controlador de la entidad Plato.
 * Gestiona el menú de comida: creación de nuevos platos, asociación con
 * Facultades,
 * imágenes de los mismos y generación de la carta al cliente y ránkings.
 */
@Controller
@RequestMapping("/plato")
public class PlatoController {

    @Autowired
    private EntityManager entityManager;

    private static final Logger log = LogManager.getLogger(PlatoController.class);

    /**
     * Introduce automáticamente variables de sesión en todas las peticiones a
     * '/plato'.
     */
    @ModelAttribute
    public void populateModel(HttpSession session, Model model) {
        for (String name : new String[] { "u", "url", "ws", "topics" }) {
            model.addAttribute(name, session.getAttribute(name));
        }
    }

    @Autowired
    private LocalData localData;

    /**
     * Obtiene una imagen genérica por si un plato no tiene foto definida.
     */
    private static InputStream defaultPic() {
        return new BufferedInputStream(Objects.requireNonNull(
                PlatoController.class.getClassLoader().getResourceAsStream(
                        "static/img/MenUni.png"))); // Una imagen por defecto para platos
    }

    /**
     * Sirve la foto particular de un plato mediante Stream (directo al navegador).
     */
    @GetMapping("/{id}/pic")
    public StreamingResponseBody getPic(@PathVariable long id) throws IOException {
        File f = localData.getFile("plato", "" + id + ".jpg");
        InputStream in = new BufferedInputStream(f.exists() ? new FileInputStream(f) : PlatoController.defaultPic());
        return os -> FileCopyUtils.copy(in, os);
    }

    /**
     * Sube y actualiza la imagen de un plato.
     * Está protegido y sólo pueden hacerlo roles administradores o gestores.
     */
    @PostMapping("/{id}/pic")
    @Transactional
    public String setPic(@RequestParam("photo") MultipartFile photo, @PathVariable long id,
            HttpSession session) throws IOException {

        // Comprueba si existe un usuario logueado en sesión
        Object userInSession = session.getAttribute("u");
        if (userInSession == null) {
            log.warn("Intento de subir foto sin usuario en sesión");
            return "redirect:/login";
        }

        // Verificación de roles (Admin o Gestor)
        User requester = (User) userInSession;
        if (!requester.hasRole(User.Role.ADMIN) && !requester.hasRole(User.Role.GESTOR_CAFETERIA)) {
            log.warn("Usuario {} intentó subir foto sin ser ADMIN o GESTOR_CAFETERIA", requester.getUsername());
            throw new UserController.NoEsTuPerfilException(); // Lanza un 403 HTTP
        }

        // Obtener la carpeta destino
        File f = localData.getFile("plato", "" + id + ".jpg");

        // Crea la carpeta "plato" físicamente si no existiera
        if (f.getParentFile() != null && !f.getParentFile().exists()) {
            f.getParentFile().mkdirs();
        }

        // Si se envió un archivo real, lo escribimos en disco
        if (!photo.isEmpty()) {
            try (BufferedOutputStream stream = new BufferedOutputStream(new FileOutputStream(f))) {
                byte[] bytes = photo.getBytes();
                stream.write(bytes);
            } catch (Exception e) {
                log.warn("Error subiendo foto de plato " + id, e);
            }
        }
        return "redirect:/plato/gestor";
    }

    /**
     * VISTA PÚBLICA: Carta general de todos los platos disponibles en el sistema.
     */
    @GetMapping
    public String plato(Model model) {
        List<Plato> listaplatos = entityManager.createQuery("SELECT p FROM Plato p", Plato.class).getResultList();
        model.addAttribute("platos", listaplatos);
        return "plato"; // Renderiza plato.html
    }

    /**
     * VISTA PÚBLICA FILTRADA: Muestra la carta limitándose a los platos que se
     * sirven en una Facultad en concreto.
     */
    @GetMapping("/facultad/{id}")
    public String platosPorFacultad(@PathVariable long id, Model model) {
        Facultad f = entityManager.find(Facultad.class, id);
        if (f == null) {
            return "redirect:/facultades";
        }
        model.addAttribute("platos", f.getPlatos());
        model.addAttribute("facultadSeleccionada", f);
        return "plato";
    }

    /**
     * VISTA PÚBLICA: Ránking. Envía la lista de platos a la vista para ser
     * mostrados por su puntuación.
     */
    @GetMapping("/ranking")
    public String ranking(Model model) {
        List<Plato> listaplatos = entityManager.createQuery("SELECT p FROM Plato p", Plato.class).getResultList();
        model.addAttribute("platos", listaplatos);
        return "ranking"; // Renderiza ranking.html
    }

    /**
     * VISTA GESTOR: Interfaz de administración del menú (Crear platos,
     * borrarlos...).
     */
    @GetMapping("/gestor")
    public String managePlatos(Model model) {
        log.info("Admin accede a la gestión de platos");
        List<Plato> platos = entityManager.createQuery("select p from Plato p", Plato.class).getResultList();
        List<Facultad> facultades = entityManager.createQuery("select f from Facultad f", Facultad.class)
                .getResultList();

        model.addAttribute("platos", platos);
        model.addAttribute("facultades", facultades);
        return "gestor"; // Renderiza gestor.html
    }

    /**
     * GESTOR: Crea un nuevo plato, establece en qué Facultades se sirve, y sube su
     * foto inicial.
     */
    @PostMapping("/gestor/addPlato")
    @Transactional
    public String addPlato(@RequestParam String nombre, @RequestParam String descripcion,
            @RequestParam double precio, @RequestParam("photo") MultipartFile photo,
            @RequestParam(required = false) List<Long> facultadIds) {

        // 1. Configurar la entidad
        Plato p = new Plato();
        p.setNombre(nombre);
        p.setDescripcion(descripcion);
        p.setPrecio(precio);
        p.setActivo(true);

        // Relacionar este plato con las facultades enviadas desde los checkboxes
        if (facultadIds != null && !facultadIds.isEmpty()) {
            Set<Facultad> facultadesAsignadas = new HashSet<>();
            for (Long id : facultadIds) {
                Facultad f = entityManager.find(Facultad.class, id);
                if (f != null)
                    facultadesAsignadas.add(f);
            }
            p.setFacultades(facultadesAsignadas);
        }

        entityManager.persist(p);
        entityManager.flush(); // Para obtener el ID autogenerado

        // 2. Guardar el archivo físicamente usando el ID generado
        if (!photo.isEmpty()) {
            File f = localData.getFile("plato", "" + p.getId() + ".jpg");
            try (BufferedOutputStream stream = new BufferedOutputStream(new FileOutputStream(f))) {
                byte[] bytes = photo.getBytes();
                stream.write(bytes);
                log.info("Foto del plato {} guardada correctamente en {}", p.getId(), f.getAbsolutePath());
            } catch (Exception e) {
                log.warn("Error al subir la foto del plato " + p.getId(), e);
            }
        }
        return "redirect:/plato/gestor";
    }

    /**
     * GESTOR: Edita un plato existente.
     */
    @PostMapping("/gestor/editPlato/{id}")
    @Transactional
    public String editPlato(@PathVariable long id, @RequestParam String nombre, @RequestParam String descripcion,
            @RequestParam double precio, @RequestParam(required = false) List<Long> facultadIds) {

        Plato p = entityManager.find(Plato.class, id);
        if (p != null) {
            p.setNombre(nombre);
            p.setDescripcion(descripcion);
            p.setPrecio(precio);

            p.getFacultades().clear();
            if (facultadIds != null && !facultadIds.isEmpty()) {
                for (Long facId : facultadIds) {
                    Facultad f = entityManager.find(Facultad.class, facId);
                    if (f != null)
                        p.getFacultades().add(f);
                }
            }
        }
        return "redirect:/plato/gestor";
    }

    /**
     * GESTOR: Elimina un plato de la base de datos permanentemente.
     */
    @PostMapping("/gestor/deletePlato/{id}")
    @Transactional
    public String deletePlato(@PathVariable long id) {
        Plato p = entityManager.find(Plato.class, id);
        if (p != null) {
            // Desasociar antes de eliminar para mantener integridad referencial
            p.getFacultades().clear();
            entityManager.remove(p);
        }
        return "redirect:/plato/gestor";
    }

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    /**
     * MONITOR DE COCINA: Muestra los pedidos entrantes para los gestores de cafetería.
     */
    @GetMapping("/cocina")
    public String monitorCocina(Model model, HttpSession session) {
        User requester = (User) session.getAttribute("u");
        // Filtro estricto de seguridad: Solo gestores de cafetería o admins
        if (requester == null || (!requester.hasRole(User.Role.ADMIN) && !requester.hasRole(User.Role.GESTOR_CAFETERIA))) {
            return "redirect:/login";
        }

        // Recuperar los pedidos en preparación o solicitados ordenados cronológicamente
        List<Pedido> pedidos = entityManager.createQuery(
                "SELECT p FROM Pedido p WHERE p.estado IN (:estados) ORDER BY p.fechaCompra ASC", Pedido.class)
                .setParameter("estados", List.of(Pedido.Estado.SOLICITADO, Pedido.Estado.PREPARANDO))
                .getResultList();

        model.addAttribute("pedidos", pedidos);
        return "cocina"; // Renderiza cocina.html
    }

    /**
     * AJAX/REST: Avanza el estado del pedido en cocina y notifica al usuario por WebSocket.
     */
    @PostMapping("/cocina/completar/{id}")
    @Transactional
    @ResponseBody
    public String avanzarEstado(@PathVariable long id, HttpSession session, jakarta.servlet.http.HttpServletResponse response) throws IOException {
        User requester = (User) session.getAttribute("u");
        if (requester == null || (!requester.hasRole(User.Role.ADMIN) && !requester.hasRole(User.Role.GESTOR_CAFETERIA))) {
            response.sendError(403, "No autorizado");
            return null;
        }

        Pedido p = entityManager.find(Pedido.class, id);
        if (p == null) {
            return "{\"status\":\"error\",\"message\":\"Pedido no encontrado\"}";
        }
        // Avanzar la máquina de estados del pedido
        if (p.getEstado() == Pedido.Estado.SOLICITADO) {
            p.setEstado(Pedido.Estado.PREPARANDO);
        } else if (p.getEstado() == Pedido.Estado.PREPARANDO) {
            p.setEstado(Pedido.Estado.LISTO_PARA_RECOGER);

            // Si está listo, le enviamos un aviso WebSocket inmediato al cliente del pedido
            String notificacionUsuario = "{"
                    + "\"type\": \"PEDIDO_LISTO\","
                    + "\"pedidoId\": " + p.getId()
                    + "}";
            messagingTemplate.convertAndSend("/user/" + p.getCliente().getUsername() + "/queue/updates", notificacionUsuario);
        }

        return "{\"status\":\"ok\",\"nuevoEstado\":\"" + p.getEstado().name() + "\"}";
    }
}
