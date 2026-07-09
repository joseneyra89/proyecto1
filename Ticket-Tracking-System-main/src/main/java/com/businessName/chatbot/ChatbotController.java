package com.businessName.chatbot;

import io.javalin.http.Context;
import io.javalin.http.Handler;
import com.google.gson.Gson;

public class ChatbotController {

    private static final ChatbotService chatbotService = new ChatbotService();
    private static final Gson gson = new Gson();

    public static Handler recibirMensaje = ctx -> {
        // Leemos el mensaje del cuerpo de la petición
        String mensajeUsuario = ctx.body();
        String mensajeLimpio = mensajeUsuario.replace("\"", "");
        
        // Procesamos la respuesta con nuestro servicio
        ChatbotResponse respuesta = chatbotService.procesarMensaje(mensajeLimpio);
        
        // Respondemos en formato JSON usando Gson
        ctx.contentType("application/json");
        ctx.result(gson.toJson(respuesta));
    };
}

