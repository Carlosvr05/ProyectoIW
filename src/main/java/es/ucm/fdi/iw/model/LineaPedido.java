package es.ucm.fdi.iw.model;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Data
@NoArgsConstructor
public class LineaPedido{
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "gen")
    @SequenceGenerator(name = "gen", sequenceName = "gen")
    private long id;

    @ManyToOne
    private Plato plato; // Muchas líneas de pedido pueden referirse al mismo plato

    @ManyToOne
    private Facultad facultad; // Cafetería desde la que se pide el plato

    private int cantidad;
    private double precioUnitario;
   
}

