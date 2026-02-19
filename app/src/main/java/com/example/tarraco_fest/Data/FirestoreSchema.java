package com.example.tarraco_fest.Data;

public final class FirestoreSchema {

    private FirestoreSchema() {}

    public static final class Collections {
        public static final String EVENTOS = "eventos";
        public static final String USUARIOS = "usuarios";

        private Collections() {}
    }

    public static final class Subcollections {
        public static final String RECORDATORIOS = "recordatorios";

        private Subcollections() {}
    }

    public static final class EventoFields {
        public static final String TITULO = "titulo";
        public static final String INICIO = "inicio";
        public static final String LUGAR_NOMBRE = "lugarNombre";
        public static final String ACTIVO = "activo";

        private EventoFields() {}
    }

    public static final class UsuarioFields {
        public static final String METODO_AUTH = "metodoAuth";
        public static final String METODOS_VINCULADOS = "metodosVinculados";
        public static final String NOMBRE_MOSTRADO = "nombreMostrado";
        public static final String EMAIL = "email";
        public static final String TELEFONO = "telefono";
        public static final String CIUDAD = "ciudad";
        public static final String BIOGRAFIA = "biografia";
        public static final String ROL = "rol";
        public static final String BLOQUEADO = "bloqueado";
        public static final String CREADO_EN = "creadoEn";
        public static final String UPDATED_AT = "updatedAt";
        public static final String ACEPTA_TERMINOS = "aceptaTerminos";
        public static final String ACEPTA_TERMINOS_EN = "aceptaTerminosEn";
        public static final String TIENE_PASSWORD = "tienePassword";
        public static final String PASSWORD_VINCULADA_EN = "passwordVinculadaEn";

        private UsuarioFields() {}
    }

    public static final class UserRoles {
        public static final String ADMIN = "admin";
        public static final String USUARIO = "usuario";

        private UserRoles() {}
    }

    public static final class AuthMethods {
        public static final String EMAIL = "email";
        public static final String GOOGLE = "google";

        private AuthMethods() {}
    }

    public static final class RecordatorioFields {
        public static final String EVENT_ID = "eventId";
        public static final String CREATED_AT = "createdAt";

        private RecordatorioFields() {}
    }
}
