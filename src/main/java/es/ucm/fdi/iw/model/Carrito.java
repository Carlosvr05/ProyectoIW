package es.ucm.fdi.iw.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.ArrayList;
import java.util.List;

@Entity
@Data
@NoArgsConstructor
public class Carrito {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "gen")
    @SequenceGenerator(name = "gen", sequenceName = "gen")
    private long id;

    @OneToOne
    private User cliente; // Un carrito suele estar asociado de forma única a un usuario activo

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "carrito_id") // Reutilizamos LineaPedido para los elementos del carrito
    private List<LineaPedido> items = new ArrayList<>();

    /**
     * Calcula el precio total de los platos en el carrito multiplicando 
     * el precio de cada plato por su cantidad.
     */
    public double getTotal() {
        return items.stream()
                .mapToDouble(i -> i.getPlato().getPrecio() * i.getCantidad())
                .sum();
    }
}