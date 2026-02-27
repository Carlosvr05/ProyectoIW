package es.ucm.fdi.iw.model;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Entity
@Data
@NoArgsConstructor
public class Plato {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "gen")
    @SequenceGenerator(name = "gen", sequenceName = "gen")
    private long id;
    private String nombre;
    private String descripcion;
    private String imagen; //Esto es la URL de la imagen para no tener que ponerla 
    private boolean activo; 
    private double precio;
    
    @ElementCollection
    private List<String> facultades;

    @ElementCollection
    private List<String> alergenos;
}
