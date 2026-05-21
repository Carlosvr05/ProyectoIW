package es.ucm.fdi.iw.model;

import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.NamedQueries;
import jakarta.persistence.NamedQuery;
import jakarta.persistence.OneToMany;
import jakarta.persistence.SequenceGenerator;

import lombok.Data;

/**
 * Entidad que representa un "Tema", Grupo o Sala de Chat.
 * Agrupa a varios usuarios (members) y guarda el historial de mensajes (messages)
 * que han sido enviados dentro de este contexto.
 */
@Data // Genera getters, setters, toString, equals y hashCode automáticamente vía Lombok
@Entity // Entidad JPA (será una tabla en la BD)
@NamedQueries({
  // Consulta rápida por JPQL para encontrar un Topic buscando por su clave única ('key')
  @NamedQuery(name = "Topic.byKey", query = "SELECT t FROM Topic t "
      + "WHERE t.key = :key")
})
public class Topic {

  @Id // Clave primaria
  @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "gen")
  @SequenceGenerator(name = "gen", sequenceName = "gen")
  private long id;

  // Relación Muchos-A-Muchos: Un Topic tiene múltiples usuarios y un usuario pertenece a múltiples Topics.
  // Esta es la entidad "propietaria" de la relación bidireccional (ver mappedBy en User)
  @ManyToMany
  private List<User> members = new ArrayList<>();
  
  private String name; // Nombre visual del grupo (ej: "Soporte Técnico")
  
  @Column(nullable = false, unique = true, name="topic_key") // 'key' es una palabra reservada en SQL, por eso se renombra la columna
  private String key; // Identificador único interno para el enrutamiento del chat (ej: "soporte-123")

  // Relación Uno-A-Muchos: Un Topic contiene muchos mensajes
  @OneToMany
  @JoinColumn(name = "topic_id") // Se guarda el ID del Topic como clave foránea en cada Mensaje
  private List<Message> messages = new ArrayList<>();

  @Override
  public String toString() {
    return name + " (" + key + ")";
  }
}
