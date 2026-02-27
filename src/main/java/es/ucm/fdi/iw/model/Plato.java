package es.ucm.fdi.iw.model;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.util.ArrayList;
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
    
    @ManyToMany
    @JoinTable(
        name = "plato_facultades", // Nombre de la tabla intermedia
        joinColumns = @JoinColumn(name = "plato_id"),
        inverseJoinColumns = @JoinColumn(name = "facultad_id")
    )
    @EqualsAndHashCode.Exclude // Para calcular quién es el plato actual, no mira la lista de facultades, mira solo el ID del plato, el nombre, etc...;
    @ToString.Exclude          // Esto hace que no se haga un bucle porque plato llama a facultad y facultad a plato otra vez
    private List<Facultad> facultades = new ArrayList<>();

    @ElementCollection
    private List<String> alergenos;
}
