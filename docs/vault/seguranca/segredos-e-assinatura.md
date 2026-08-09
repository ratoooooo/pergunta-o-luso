# Segredos e assinatura de release

← [índice](../00-indice.md)

## Ficheiros que NUNCA entram no repositório

| Ficheiro | Porquê | Linha no `.gitignore` |
|---|---|---|
| `app/google-services.json` | API key + app id do projeto Firebase | `google-services.json` |
| `upload-keystore.jks` | chave de upload da Play Store | `*.jks` |
| `keystore.properties` | passwords do keystore | `keystore.properties` |

Confirmável a qualquer momento:

```bash
git ls-files | grep -iE "google-services\.json|\.jks$|^keystore\.properties$"   # tem de dar vazio
git check-ignore -v app/google-services.json upload-keystore.jks keystore.properties
```

`keystore.properties.example` (com valores vazios) **é** commitado — é o modelo.

## Assinatura

**Play App Signing**, decidido explicitamente: a Google guarda a chave definitiva e localmente só
existe a **chave de upload**. Se a de upload se perder, pede-se reset à Google. Com assinatura
própria tradicional, perder a chave significa perder para sempre a capacidade de atualizar a app
e obriga a publicar ficha nova. É também o que o formato `.aab` exige.

`app/build.gradle.kts` lê de `keystore.properties` ou, em alternativa, das variáveis
`POL_STORE_FILE`, `POL_STORE_PASSWORD`, `POL_KEY_ALIAS`, `POL_KEY_PASSWORD`.

**Sem chave configurada o release compila na mesma e sai por assinar** — deliberadamente, em vez
de cair no certificado de debug: um APK assinado em debug instala por `adb` e é **recusado pela
Play Store**, o que daria um falso positivo tardio.

APK de release assinado: **~2,3 MB** contra ~18 MB do debug (`isMinifyEnabled` +
`isShrinkResources`).

## R8 — a armadilha que não era óbvia

A análise inicial concluiu "risco baixo, a app não usa desserialização por reflexão da RTDB".
Correto quanto aos **modelos de dados** — e à mesma incompleto.

O SDK do Firebase **descobre componentes por reflexão**, lendo nomes de classe do AndroidManifest
e instanciando-os com o construtor sem argumentos. O R8 não vê essas chamadas e remove o
construtor. O primeiro release assinado registou:

```
NoSuchMethodException: ...FirebaseAuthLegacyRegistrar.<init> []
```

Sem impacto funcional nesse caso (é um shim que só regista a versão da lib) — mas é o **mesmo
mecanismo** que carrega o `FirebaseAuthRegistrar` e o `DatabaseRegistrar`, que sobreviveram **por
acaso, não por desenho**. Regra que o torna determinístico:

```
-keep class * implements com.google.firebase.components.ComponentRegistrar { <init>(); }
```

Depois disto: zero avisos `ComponentDiscovery`.

**O `mapping.txt`** fica em `app/build/outputs/mapping/release/` e **tem de ser guardado a cada
release publicado**, fora do repositório — sem ele um stack trace de produção é ilegível.

## Páginas legais

Servidas por GitHub Pages a partir de `/docs` no branch **`gh-pages`**:

- <https://ratoooooo.github.io/pergunta-o-luso/privacidade.html>
- <https://ratoooooo.github.io/pergunta-o-luso/eliminar-conta.html>

A política é explícita sobre o que fica visível a outros jogadores (nome, avatar, nível, stats,
estado online, conteúdo dos quizzes) e sobre o e-mail **nunca** ser mostrado nem guardado na base
de dados de jogo.

> **Por fazer:** o commit `0a14609` da `gh-pages` (declarar a permissão `VIBRATE`) **está só
> local**. Publicar é decisão do dono: `git push origin gh-pages`.

Ver também: [historico-vulnerabilidades](historico-vulnerabilidades.md) ·
[firebase](../arquitetura/firebase.md)
