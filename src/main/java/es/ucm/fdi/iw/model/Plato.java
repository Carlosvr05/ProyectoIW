package main.java.es.ucm.fdi.iw.model;
import jakarta.persistence.*;
import lombok.Data;

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
