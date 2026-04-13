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
import org.springframework.web.bind.annotation.ResponseBody;
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


@Controller
@RequestMapping("/facultades")
public class FacultadContoller {

    @Autowired
    private LocalData localData;

    @GetMapping("/{id}/pic")
    public StreamingResponseBody getPic(@PathVariable long id) throws IOException {
        File f = localData.getFile("facultad", "" + id + ".jpg");
        InputStream in = new BufferedInputStream(f.exists() ? 
            new FileInputStream(f) : FacultadContoller.class.getClassLoader()
                .getResourceAsStream("static/img/default-pic.jpg"));
        return os -> FileCopyUtils.copy(in, os);
    }

    @PostMapping("/{id}/pic")
    @ResponseBody
    public String setPic(@RequestParam("photo") MultipartFile photo, @PathVariable long id, 
                        HttpSession session) throws IOException {
        
        User requester = (User) session.getAttribute("u");
        if (requester == null || !requester.hasRole(User.Role.ADMIN)) {
            return "{\"error\":\"No autorizado\"}";
        }

        File f = localData.getFile("facultad", "" + id + ".jpg");
        try (BufferedOutputStream stream = new BufferedOutputStream(new FileOutputStream(f))) {
            stream.write(photo.getBytes());
        }
        return "{\"status\":\"foto de facultad subida\"}";
    }
    
    @ModelAttribute
    public void populateModel(HttpSession session, Model model) {
        for (String name : new String[] { "u", "url", "ws", "topics"}) {
        model.addAttribute(name, session.getAttribute(name));
        }
    }

    @GetMapping  //Ruta 
    public String facu(Model model) {  //nombre de la funcion da igual
        return "facultades";    //nombre de vista
    }
}
