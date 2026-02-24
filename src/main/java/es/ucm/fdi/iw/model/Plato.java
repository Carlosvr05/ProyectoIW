package main.java.es.ucm.fdi.iw.model;
import es.ucm.fdi.iw.model.Transferable;
import es.ucm.fdi.iw.model.User;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Data
@NoArgsConstructor
public class Plato {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "gen")
    @SequenceGenerator(name = "gen", sequenceName = "gen")
    private long id;
    private String nombre;
    private double precio;
}
