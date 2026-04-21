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
import jakarta.persistence.EntityManager;
import jakarta.servlet.http.HttpSession;
import jakarta.transaction.Transactional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Controller
@RequestMapping("/plato")
public class PlatoController {
    
    @Autowired
    private EntityManager entityManager;

    private static final Logger log = LogManager.getLogger(PlatoController.class);

    @ModelAttribute
    public void populateModel(HttpSession session, Model model) {
        for (String name : new String[] { "u", "url", "ws", "topics"}) {
        model.addAttribute(name, session.getAttribute(name));
        }
    }

    @Autowired
    private LocalData localData;

    private static InputStream defaultPic() {
        return new BufferedInputStream(Objects.requireNonNull(
            PlatoController.class.getClassLoader().getResourceAsStream(
                "static/img/MenUni.png"))); // Una imagen por defecto para platos
    }

    @GetMapping("/{id}/pic")
    public StreamingResponseBody getPic(@PathVariable long id) throws IOException {
        File f = localData.getFile("plato", "" + id + ".jpg");
        InputStream in = new BufferedInputStream(f.exists() ? 
            new FileInputStream(f) : PlatoController.defaultPic());
        return os -> FileCopyUtils.copy(in, os);
    }

    @PostMapping("/{id}/pic")
    @Transactional
    public String setPic(@RequestParam("photo") MultipartFile photo, @PathVariable long id, 
                        HttpSession session) throws IOException {
        
        // Solo el ADMIN puede cambiar fotos de platos
        // En lugar de session.getAttribute, comprueba si existe para evitar el cuelgue
        Object userInSession = session.getAttribute("u");
        if (userInSession == null) {
            log.warn("Intento de subir foto sin usuario en sesión");
            return "redirect:/login"; 
        }
        
        User requester = (User) userInSession;
        if (!requester.hasRole(User.Role.ADMIN) && !requester.hasRole(User.Role.GESTOR_CAFETERIA)) {
            log.warn("Usuario {} intentó subir foto sin ser ADMIN o GESTOR_CAFETERIA", requester.getUsername());
            throw new UserController.NoEsTuPerfilException();
        }

        File f = localData.getFile("plato", "" + id + ".jpg");
        // Crea la carpeta "plato" si es la primera vez que subes una foto
        if (f.getParentFile() != null && !f.getParentFile().exists()) {
            f.getParentFile().mkdirs();
        }

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

    @GetMapping
    public String plato(Model model) {
        List<Plato> listaplatos = entityManager.createQuery("SELECT p FROM Plato p", Plato.class).getResultList();
        model.addAttribute("platos", listaplatos);
        return "plato";
    }

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

    @GetMapping("/ranking")
    public String ranking(Model model) {
        List<Plato> listaplatos = entityManager.createQuery("SELECT p FROM Plato p", Plato.class).getResultList();
        model.addAttribute("platos", listaplatos);
        return "ranking";
    }

    @GetMapping("/gestor")
    public String managePlatos(Model model) {
      log.info("Admin accede a la gestión de platos");
      List<Plato> platos = entityManager.createQuery("select p from Plato p", Plato.class).getResultList();
      List<Facultad> facultades = entityManager.createQuery("select f from Facultad f", Facultad.class).getResultList();
      model.addAttribute("platos", platos);
      model.addAttribute("facultades", facultades);
      return "gestor"; 
    }

    @PostMapping("/gestor/addPlato")
    @Transactional
    public String addPlato(@RequestParam String nombre, @RequestParam String descripcion,
            @RequestParam double precio, @RequestParam("photo") MultipartFile photo,
            @RequestParam(required = false) List<Long> facultadIds) {
        Plato p = new Plato();
        p.setNombre(nombre);
        p.setDescripcion(descripcion);
        p.setPrecio(precio);
        p.setActivo(true);
        
        if (facultadIds != null && !facultadIds.isEmpty()) {
            Set<Facultad> facultadesAsignadas = new HashSet<>();
            for (Long id : facultadIds) {
                Facultad f = entityManager.find(Facultad.class, id);
                if (f != null) facultadesAsignadas.add(f);
            }
            p.setFacultades(facultadesAsignadas);
        }
        entityManager.persist(p);
        entityManager.flush();
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

    @PostMapping("/gestor/deletePlato/{id}")
    @Transactional
    public String deletePlato(@PathVariable long id) {
        Plato p = entityManager.find(Plato.class, id);
        if (p != null) {
            p.getFacultades().clear(); 
            entityManager.remove(p);
        }
        return "redirect:/plato/gestor";
    }
}

