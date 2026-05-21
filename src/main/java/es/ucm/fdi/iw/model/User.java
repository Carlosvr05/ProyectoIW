package es.ucm.fdi.iw.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Entidad JPA que representa a un Usuario autorizado en el sistema.
 * Almacena sus credenciales, saldo, roles y establece las relaciones
 * con los mensajes y los grupos (Topics) a los que pertenece.
 */
@Entity // Indica a Hibernate que esta clase debe guardarse en la Base de Datos
@Data // Anotación de Lombok: Genera automáticamente Getters, Setters, toString, equals y hashCode
@NoArgsConstructor // Anotación de Lombok: Genera el constructor vacío (obligatorio para JPA)
@NamedQueries({
    // Consultas predefinidas de JPQL para optimizar el rendimiento y la legibilidad
    @NamedQuery(name = "User.byUsername", query = "SELECT u FROM User u "
        + "WHERE u.username = :username AND u.enabled = TRUE"),
    @NamedQuery(name = "User.hasUsername", query = "SELECT COUNT(u) "
        + "FROM User u "
        + "WHERE u.username = :username"),
    @NamedQuery(name = "User.topics", query = "SELECT t.key "
        + "FROM Topic t JOIN t.members u "
        + "WHERE u.id = :id")
})
@Table(name = "IWUser") // Nombre de la tabla en base de datos (se usa IWUser porque 'User' es palabra reservada en SQL)
public class User implements Transferable<User.Transfer> {

  /**
   * Roles disponibles en el sistema.
   * Definen a qué partes de la web puede acceder un usuario en base
   * a la configuración de SecurityConfig.
   */
  public enum Role {
    USER,             // Usuario normal (puede comprar)
    ADMIN,            // Administrador del sistema
    GESTOR_CAFETERIA, // Gestor (puede gestionar platos y menús)
  }

  @Id // Indica que este campo es la Clave Primaria (Primary Key)
  @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "gen")
  @SequenceGenerator(name = "gen", sequenceName = "gen")
  private long id;

  @Column(nullable = false, unique = true) // El username es obligatorio y no puede haber dos iguales
  private String username;
  
  @Column(nullable = false)
  private String password; // Contraseña encriptada (con BCrypt)

  private String firstName;
  private String lastName;
  private Double money;

  private String colorFavorito;

  private boolean enabled; // Indica si la cuenta está activa (true) o deshabilitada/baneada (false)
  private String roles;    // Lista de roles separados por coma (ej: "USER,ADMIN")

  // Relación 1:N - Un usuario puede enviar muchos mensajes
  @OneToMany
  @JoinColumn(name = "sender_id") // Clave foránea en la tabla Message
  private List<Message> sent = new ArrayList<>();
  
  // Relación 1:N - Un usuario puede recibir muchos mensajes
  @OneToMany
  @JoinColumn(name = "recipient_id") // Clave foránea en la tabla Message
  private List<Message> received = new ArrayList<>();
  
  // Relación N:M - Un usuario pertenece a muchos topics, un topic tiene muchos usuarios
  // 'mappedBy' indica que la entidad 'Topic' (campo 'members') es la dueña de la relación
  @ManyToMany(mappedBy = "members")
  private List<Topic> groups = new ArrayList<>();

  /**
   * Comprueba si el usuario tiene un rol específico.
   * 
   * @param role Rol a comprobar (del enum Role)
   * @return true si el usuario posee dicho rol.
   */
  public boolean hasRole(Role role) {
    String roleName = role.name();
    return Arrays.asList(roles.split(",")).contains(roleName);
  }

  /**
   * DTO (Data Transfer Object) interno.
   * Se usa para transformar el Usuario a un objeto JSON plano que pueda
   * enviarse por red (API o WebSockets) sin generar recursión infinita
   * con las relaciones (ej. User -> Message -> User -> Message...).
   */
  @Getter
  @AllArgsConstructor
  public static class Transfer {
    private long id;
    private String username;
    private int totalReceived;
    private int totalSent;
    private String groups;
  }

  /**
   * Construye un objeto Transfer (DTO) a partir de los datos de este User.
   */
  @Override
  public Transfer toTransfer() {
    StringBuilder gs = new StringBuilder();
    for (Topic g : groups) {
      gs.append(g.getName()).append(", ");
    } 
    return new Transfer(id, username, received.size(), sent.size(), gs.toString());
  }

  @Override
  public String toString() {
    return toTransfer().toString();
  }
}
