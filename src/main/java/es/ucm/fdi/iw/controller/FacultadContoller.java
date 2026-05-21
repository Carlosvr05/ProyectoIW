package es.ucm.fdi.iw.controller;

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
import es.ucm.fdi.iw.model.User;
import jakarta.servlet.http.HttpSession;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import es.ucm.fdi.iw.model.Facultad;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Controlador para la gestión de Facultades.
 * Permite listar las facultades al público, administrarlas (añadir/eliminar)
 * por parte de los administradores y gestionar sus fotografías asociadas.
 */
@Controller
@RequestMapping("/facultades")
public class FacultadContoller {

    @Autowired
    private LocalData localData;

    @Autowired
    private EntityManager entityManager;

    private static final Logger log = LogManager.getLogger(FacultadContoller.class);

    /**
     * Sirve la imagen de portada asociada a una Facultad.
     * Si la facultad no tiene foto propia subida, devuelve una por defecto.
     * 
     * @param id ID de la facultad
     * @return El stream de bytes de la imagen
     */
    @GetMapping("/{id}/pic")
    public StreamingResponseBody getPic(@PathVariable long id) throws IOException {
        File f = localData.getFile("facultad", "" + id + ".jpg");
        InputStream in = new BufferedInputStream(f.exists() ? new FileInputStream(f)
                : FacultadContoller.class.getClassLoader()
                        .getResourceAsStream("static/img/default-pic.jpg"));
        return os -> FileCopyUtils.copy(in, os); // Escribe directamente en el response
    }

    /**
     * Actualiza la fotografía de una Facultad.
     * Sólo accesible para usuarios con el rol ADMIN.
     */
    @PostMapping("/{id}/pic")
    @Transactional
    public String setPic(@RequestParam("photo") MultipartFile photo, @PathVariable long id,
            HttpSession session) throws IOException {

        User requester = (User) session.getAttribute("u");
        // Filtro manual de permisos
        if (requester == null || !requester.hasRole(User.Role.ADMIN)) {
            return "redirect:/login";
        }

        File f = localData.getFile("facultad", "" + id + ".jpg");
        // Crea la carpeta "facultad" en el sistema de archivos si no existía previamente
        if (f.getParentFile() != null && !f.getParentFile().exists()) {
            f.getParentFile().mkdirs();
        }

        if (!photo.isEmpty()) {
            try (BufferedOutputStream stream = new BufferedOutputStream(new FileOutputStream(f))) {
                stream.write(photo.getBytes());
            } catch (Exception e) {
                log.warn("Error subiendo foto de facultad " + id, e);
            }
        }
        return "redirect:/facultades/gestor";
    }

    /**
     * Pasa las variables globales de sesión al modelo de Thymeleaf.
     */
    @ModelAttribute
    public void populateModel(HttpSession session, Model model) {
        for (String name : new String[] { "u", "url", "ws", "topics" }) {
            model.addAttribute(name, session.getAttribute(name));
        }
    }

    /**
     * VISTA PÚBLICA: Lista de todas las facultades.
     * Renderiza 'facultades.html'.
     */
    @GetMapping // Atiende peticiones directas a "/facultades"
    public String facu(Model model) { 
        List<Facultad> facultades = entityManager.createQuery("SELECT f FROM Facultad f", Facultad.class)
                .getResultList();
        model.addAttribute("facultades", facultades);
        return "facultades"; 
    }

    /**
     * VISTA GESTOR: Panel de administración interno de Facultades.
     * Renderiza 'gestor_facultades.html'
     */
    @GetMapping("/gestor")
    public String manageFacultades(Model model) {
        log.info("Admin accede a la gestión de facultades");
        List<Facultad> facultades = entityManager.createQuery("SELECT f FROM Facultad f", Facultad.class)
                .getResultList();
        model.addAttribute("facultades", facultades);
        return "gestor_facultades";
    }

    /**
     * GESTOR: Añade una nueva facultad a la base de datos, junto a su imagen.
     */
    @PostMapping("/gestor/addFacultad")
    @Transactional
    public String addFacultad(@RequestParam String nombre, @RequestParam String ubicacion,
            @RequestParam String descripcion, @RequestParam String horario,
            @RequestParam String aforo, @RequestParam("photo") MultipartFile photo) {

        // 1. Crear el objeto con los datos del formulario
        Facultad f = new Facultad();
        f.setNombre(nombre);
        f.setUbicacion(ubicacion);
        f.setDescripcion(descripcion);
        f.setHorario(horario);
        f.setAforo(aforo);

        // 2. Persistir en la BD
        entityManager.persist(f);
        entityManager.flush(); // Fuerza la escritura para que la BD le asigne un ID (necesario para el nombre de la foto)

        // 3. Procesar y guardar el archivo de foto físicamente
        if (!photo.isEmpty()) {
            File picFile = localData.getFile("facultad", "" + f.getId() + ".jpg");
            if (picFile.getParentFile() != null && !picFile.getParentFile().exists()) {
                picFile.getParentFile().mkdirs();
            }
            try (BufferedOutputStream stream = new BufferedOutputStream(new FileOutputStream(picFile))) {
                stream.write(photo.getBytes());
                log.info("Foto de la facultad {} guardada correctamente", f.getId());
            } catch (Exception e) {
                log.warn("Error al subir la foto de la facultad " + f.getId(), e);
            }
        }
        return "redirect:/facultades/gestor";
    }

    /**
     * GESTOR: Borra una facultad existente y desvincula los platos asignados a ella.
     */
    @PostMapping("/gestor/deleteFacultad/{id}")
    @Transactional
    public String deleteFacultad(@PathVariable long id) {
        Facultad f = entityManager.find(Facultad.class, id);
        if (f != null) {
            // Desvincular de los platos antes de borrar (rompe la relación ManyToMany)
            // de forma segura para no crear excepciones por violaciones de clave foránea.
            f.getPlatos().forEach(p -> p.getFacultades().remove(f));
            
            // Borrado definitivo
            entityManager.remove(f);
            log.info("Facultad {} eliminada", id);
        }
        return "redirect:/facultades/gestor";
    }
}
