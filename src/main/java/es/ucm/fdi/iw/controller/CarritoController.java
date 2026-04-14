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

    @ModelAttribute
    public void populateModel(HttpSession session, Model model) {
        for (String name : new String[] { "u", "url", "ws", "topics"}) {
        model.addAttribute(name, session.getAttribute(name));
        }
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

    @PostMapping("/carrito/add/{idPlato}")
    @Transactional
    public String addAlCarrito(
            @PathVariable long idPlato, 
            @RequestParam(defaultValue = "1") int cantidad,
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

    @GetMapping  //Ruta 
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
