# Regras R8 para o build de release (Fase 24).
#
# Nota de âmbito: esta app **não** usa a desserialização por reflexão da Realtime Database.
# Todos os `getValue()` são sobre primitivos (`String::class.java`, `Int::class.java`, …) e os
# modelos (`Profile`, `Question`, `ScoreEntry`, `CustomCategory`, …) são construídos campo a
# campo; as escritas são sempre `Map`. Por isso o R8 não tem por onde partir a serialização —
# que é a forma clássica de uma app Firebase rebentar só em release.
#
# As regras abaixo são deliberadamente conservadoras: as bibliotecas (Compose, Firebase,
# kotlinx-coroutines) já trazem as suas próprias `consumer-rules`, e aqui só se acrescenta o
# que protege código deste projeto ou silencia avisos conhecidos.

# --- Modelos de dados ---------------------------------------------------------------
# Mantidos com os campos intactos por segurança: se algum dia se passar a usar
# `snapshot.getValue(Profile::class.java)`, o R8 renomearia os campos e a leitura devolveria
# nulos silenciosamente — uma falha que só aparece em release e é penosa de diagnosticar.
-keepclassmembers class com.ratoooooo.perguntaoluso.data.** {
    <init>();
    <fields>;
}

# --- Firebase ------------------------------------------------------------------------
# O SDK descobre os seus componentes lendo nomes de classe do AndroidManifest e
# instanciando-os por REFLEXÃO, com o construtor sem argumentos. O R8 não vê essas chamadas
# e remove o construtor. Confirmado em runtime na Fase 26: o primeiro build de release
# assinado registou
#   NoSuchMethodException: com.google.firebase.auth.ktx.FirebaseAuthLegacyRegistrar.<init>
# Nesse caso concreto não partiu nada — é um shim de compatibilidade que só regista a versão
# da biblioteca — mas é o MESMO mecanismo que carrega o FirebaseAuthRegistrar e o
# DatabaseRegistrar. Esses sobreviveram por acaso, não por desenho; a regra abaixo torna-o
# determinístico em vez de depender de o R8 os alcançar por outro caminho.
-keep class * implements com.google.firebase.components.ComponentRegistrar {
    <init>();
}
-keepattributes Signature,*Annotation*,EnclosingMethod,InnerClasses
-keepnames class com.google.firebase.database.** { *; }
-dontwarn com.google.firebase.**

# --- Kotlin / coroutines --------------------------------------------------------------
-dontwarn kotlinx.coroutines.**
-keepclassmembers class kotlin.Metadata { *; }

# --- Compose --------------------------------------------------------------------------
# O compilador de Compose e o androidx já enviam as regras necessárias; isto só evita que
# avisos de classes ausentes façam falhar o build.
-dontwarn androidx.compose.**

# --- Diagnóstico ----------------------------------------------------------------------
# Mantém números de linha para que um stack trace de produção continue legível depois de
# desofuscado com o mapping.txt, sem revelar os nomes originais dos ficheiros.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
