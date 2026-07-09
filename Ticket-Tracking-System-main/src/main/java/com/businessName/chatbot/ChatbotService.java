package com.businessName.chatbot;

import org.springframework.stereotype.Service;

@Service
public class ChatbotService {

    public ChatbotResponse procesarMensaje(String mensajeUsuario) {
        String texto = mensajeUsuario.toLowerCase().trim();
        String respuesta;

        if (texto.contains("hola") || texto.contains("buenos") || texto.contains("buenas")) {
            respuesta = "¡Hola! Soy el asistente virtual del Sistema de Tickets. ¿En qué te puedo ayudar hoy?\n\n" +
                        "1. Crear un nuevo ticket\n" +
                        "2. Consultar el estado de un ticket\n" +
                        "3. Hablar con un asesor";
        } else if (texto.contains("crear") || texto.contains("1")) {
            respuesta = "Para crear un nuevo ticket de soporte, por favor dirígete a la sección de creación en el menú principal.";
        } else if (texto.contains("estado") || texto.contains("consultar") || texto.contains("2")) {
            respuesta = "Por favor, indícame el número (ID) de tu ticket para verificar su estado en la base de datos.";
        } else if (texto.contains("asesor") || texto.contains("humano") || texto.contains("3")) {
            respuesta = "Entendido. He transferido esta conversación a un agente de soporte. Se comunicarán contigo pronto.";
        } else {
            respuesta = "Lo siento, no logré entender tu consulta. Intenta ingresando una opción (1, 2 o 3) o escribe 'hola' para reiniciar.";
        }

        return new ChatbotResponse(respuesta);
    }
}
