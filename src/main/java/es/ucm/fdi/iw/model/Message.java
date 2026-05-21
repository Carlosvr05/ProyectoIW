package es.ucm.fdi.iw.model;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.NamedQueries;
import jakarta.persistence.NamedQuery;
import jakarta.persistence.SequenceGenerator;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import lombok.Data;
import lombok.Getter;
import lombok.AllArgsConstructor;

/**
 * Entidad JPA que representa un Mensaje enviado en el sistema.
 * Puede ser un mensaje directo entre dos usuarios (sender y recipient)
 * o un mensaje dentro de un grupo/chat (topic).
 */
@Entity
@NamedQueries({
		// Cuenta los mensajes recibidos por un usuario concreto que aún no tienen fecha de lectura
		@NamedQuery(name = "Message.countUnread", query = "SELECT COUNT(m) FROM Message m "
				+ "WHERE m.recipient.id = :userId AND m.dateRead = null")
})
@Data // Genera getters, setters y demás automáticamente
public class Message implements Transferable<Message.Transfer> {

	private static Logger log = LogManager.getLogger(Message.class);

	@Id // Clave primaria autogenerada
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "gen")
	@SequenceGenerator(name = "gen", sequenceName = "gen")
	private long id;
	
	// Remitente del mensaje (Muchos mensajes pueden pertenecer a un mismo remitente)
	@ManyToOne
	private User sender;
	
	// Destinatario del mensaje (si es mensaje privado directo)
	@ManyToOne
	private User recipient;
	
	// Grupo o sala al que se envía (si es mensaje de grupo)
	@ManyToOne
	private Topic topic;

	// Contenido textual del mensaje
	private String text;

	// Fecha y hora de envío y de lectura
	private LocalDateTime dateSent;
	private LocalDateTime dateRead;

	/**
	 * Objeto de Transferencia de Datos (DTO - Data Transfer Object).
	 * Su objetivo es extraer sólo la información necesaria del Mensaje,
	 * transformando entidades complejas en simples "Strings" (nombres, fechas),
	 * para que sea fácilmente serializable a JSON (ideal para API REST y WebSockets).
	 */
	@Getter
	@AllArgsConstructor
	public static class Transfer {
		private String from;     // Nombre del remitente
		private String to;       // Nombre del destinatario
		private String sent;     // Fecha de envío (formato ISO)
		private String received; // Fecha de lectura (formato ISO)
		private String topic;    // Nombre del topic/grupo
		private String text;     // Contenido del mensaje
		long id;                 // ID interno

		/**
		 * Constructor que convierte una entidad Message en un Transfer DTO.
		 */
		public Transfer(Message m) {
			this.from = m.getSender().getUsername();
			this.to = m.getRecipient() == null ? "null" : m.getRecipient().getUsername();
			this.topic = m.getTopic() == null ? "null" : m.getTopic().getName();
			
			// Formateo estricto de las fechas para compatibilidad con Javascript
			this.sent = DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(m.getDateSent());
			this.received = m.getDateRead() == null ? null
					: DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(m.getDateRead());
					
			this.text = m.getText();
			this.id = m.getId();
		}
	}

	/**
	 * Implementación de la interfaz Transferable.
	 * Devuelve la versión "plana" de este mensaje.
	 */
	@Override
	public Transfer toTransfer() {
		return new Transfer(this);
	}
}
