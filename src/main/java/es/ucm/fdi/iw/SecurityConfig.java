package es.ucm.fdi.iw;

import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Configuración de Seguridad de Spring Security.
 * 
 * En este fichero se centraliza la mayor parte de la configuración de seguridad
 * de la aplicación web: qué rutas son públicas, cuáles requieren login y con
 * qué
 * roles. También define los "beans" necesarios para encriptar contraseñas
 * y gestionar la autenticación.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

	@Autowired
	private Environment env;

	/**
	 * Configuración principal de seguridad (filtros HTTP).
	 * 
	 * The first rule that matches will be followed - so if a rule decides to grant
	 * access
	 * to a resource, a later rule cannot deny that access, and vice-versa.
	 * 
	 * To disable security entirely, just add an .antMatchers("**").permitAll()
	 * as a first rule. Note that this may break an application that expects to have
	 * login information available.
	 */
	@Bean
	public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {

		// 1. Configuración de la consola base de datos H2 (Sólo activa en modo debug)
		// La consola H2 requiere deshabilitar la protección CSRF y permitir iframes
		// (frameOptions)
		String debugProperty = env.getProperty("es.ucm.fdi.debug");
		if (debugProperty != null && Boolean.parseBoolean(debugProperty.toLowerCase())) {
			http.csrf(csrf -> csrf
					.ignoringRequestMatchers("/h2/**"));
			http.authorizeHttpRequests(authorize -> authorize
					.requestMatchers("/h2/**").permitAll() // <-- Permite el acceso sin login a la consola H2
			);
			http.headers(header -> header.frameOptions(frameOptions -> frameOptions.sameOrigin()));
		}

		http
				// 2. Desactivar CSRF para las peticiones a la API REST (para facilitar clientes
				// externos)
				.csrf(csrf -> csrf
						.ignoringRequestMatchers("/api/**"))
				// 3. Reglas de Autorización de Rutas (Endpoints)
				.authorizeHttpRequests(authorize -> authorize
						// Archivos estáticos y páginas de error siempre accesibles
						.requestMatchers("/css/**", "/js/**", "/img/**", "/", "/error").permitAll()
						// Endpoints de la API públicos
						.requestMatchers("/api/**").permitAll()
						// Páginas principales accesibles sin iniciar sesión
						.requestMatchers("/inicio/**").permitAll()
						.requestMatchers("/plato/ranking/**").permitAll()
						.requestMatchers("/plato/**").permitAll()
						.requestMatchers("/contacto/**").permitAll()
						.requestMatchers("/facultades/**").permitAll()
						.requestMatchers("/carrito/**").permitAll()

						// Zona exclusiva de Gestores y Administradores
						.requestMatchers("/plato/gestor/**").hasAnyRole("ADMIN", "GESTOR_CAFETERIA")
						// Zona exclusiva de Administradores del sistema
						.requestMatchers("/admin/**").hasRole("ADMIN")
						// Endpoints de usuarios comunes (perfil, mensajería...) requieren rol USER
						.requestMatchers("/user/**").hasRole("USER")

						// Obtener imágenes es público para cualquiera
						.requestMatchers(HttpMethod.GET, "/plato/*/pic", "/facultades/*/pic").permitAll()
						// Sólo Administradores pueden cambiar la foto de una facultad
						.requestMatchers(HttpMethod.POST, "/facultades/*/pic").hasRole("ADMIN")
						// Administradores y Gestores pueden cambiar la foto de un plato
						.requestMatchers(HttpMethod.POST, "/plato/*/pic").hasAnyRole("ADMIN", "GESTOR_CAFETERIA")

						// Cualquier otra ruta que no se haya mencionado arriba requerirá estar
						// autenticado
						.anyRequest().authenticated())
				// 4. Configuración del formulario de Login de Spring Security
				.formLogin(formLogin -> formLogin
						.loginPage("/login") // Especificamos nuestra vista personalizada de login
						.permitAll() // Permitimos a todo el mundo acceder a esta vista
						// handler invocado cuando el login es correcto (redirigirá al index o última
						// página)
						.successHandler(loginSuccessHandler));

		return http.build();
	}

	/**
	 * Declara el Bean encargado de encriptar contraseñas (PasswordEncoder).
	 * 
	 * Spring Security lo usa automáticamente al comprobar contraseñas en el login.
	 * Además, al declararlo como Bean, podemos inyectarlo con @Autowired en
	 * controladores (como UserController) para encriptar contraseñas de nuevos
	 * usuarios.
	 */
	@Bean
	public PasswordEncoder getPasswordEncoder() {
		// Crea un encoder inteligente que, por defecto, usa BCrypt para hashear
		// contraseñas
		return PasswordEncoderFactories.createDelegatingPasswordEncoder();
	}

	/**
	 * Declara el Bean que traduce los Usuarios de nuestra Base de Datos a un
	 * formato
	 * comprensible por Spring Security (UserDetailsService).
	 */
	@Bean
	public IwUserDetailsService springDataUserDetailsService() {
		return new IwUserDetailsService();
	}

	/**
	 * Declara el AuthenticationManager como Bean.
	 * 
	 * Este gestor de autenticación coordina el UserDetailsService (para buscar al
	 * usuario)
	 * y el PasswordEncoder (para validar que la contraseña tecleada coincide con el
	 * hash).
	 * Al hacerlo Bean, podríamos usarlo manualmente para auto-loguear usuarios
	 * recién registrados.
	 */
	@Bean
	public AuthenticationManager authenticationManager(
			UserDetailsService userDetailsService,
			PasswordEncoder passwordEncoder) {
		DaoAuthenticationProvider authenticationProvider = new DaoAuthenticationProvider();
		authenticationProvider.setUserDetailsService(userDetailsService);
		authenticationProvider.setPasswordEncoder(passwordEncoder);

		return new ProviderManager(authenticationProvider);
	}

	@Autowired
	private LoginSuccessHandler loginSuccessHandler;
}
