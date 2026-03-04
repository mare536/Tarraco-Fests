package com.example.tarraco_fest.Push;

/**
 * Contrato de constantes para notificaciones push y navegacion asociada.
 * Evita hardcodes de claves entre Service y Activities.
 */
public final class PushContract {

    private PushContract() {}

    public static final String CHANNEL_GENERAL = "tarraco_push_general";

    public static final String EXTRA_PUSH_EVENT_ID = "push_event_id";
    public static final String EXTRA_PUSH_TITLE = "push_title";
    public static final String EXTRA_PUSH_BODY = "push_body";

    public static final String DATA_EVENT_ID = "eventId";
    public static final String DATA_EVENT_ID_ALT = "eventoId";
    public static final String DATA_TITLE = "title";
    public static final String DATA_BODY = "body";
}
