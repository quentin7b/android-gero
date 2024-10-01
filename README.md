# Gero

In some very specific cases, you might need to use gettext files for translation instead of the
android `strings.xml` resources.

If so, you might be familiar with [pontoon](https://pontoon.mozilla.org/) and the [gettext format](https://www.gnu.org/software/gettext/)
This library is a best effort to help you use `po` files in your Android Application

## Install

Add this to your root `build.gradle` file if not already done

```groovy
repositories {
    maven {
        url "https://jitpack.io"
    }
}
```

And this to your module `build.gradle`

```groovy
dependencies {
    compile 'com.github.quentin7b:android-gero:1.3.2'
}
```

## How to use

### File set up

Add your `.po` files under the `assets` directory of your module.

By default, the library will look in a `po` subfolder under assets, but you can choose another name.

Once it is done make sure of two things

- All your `.po` files have the `Language:` header with the language or the iso tag (`fr` or `fr-FR`)
- All your `.po` files have the `Plural-Forms:` header defined

For the rest, you can go with it.

### Loading  locale

**Gero** will not load files at startup, it will wait for you to ask.

In order to do so, call : `setLocaleAsync`

> Note that this method runs `async`

For example:

```kotlin
SomeCoroutineScope.launch {
    Gero.setLocaleAsync(baseContext, Locale.FRANCE).await()
}
```

If you need a fallback language, like english if `Gero` can't find the translation in the other files,
you can specify it using the `fallbackLocale: Locale` argument.

By default, this parameter is set to `Locale.US`, if you want, for example, German, use it this way

```kotlin
SomeCoroutineScope.launch {
    Gero.setLocaleAsync(
        baseContext,
        Locale.FRANCE,
        fallbackLocale = Locale.GERMANY
    ).await()
}
```

This method will do 2 things:

1. Check all the files under the assets subfolder and register which file goes for which language
> One language can have multiple files, this is done only once in the app lifetime

2. For all the files matching the language given, register its key values
> Including the plurals

### Using  it

Once the loading is done, you can use **Gero** directly with 2 methods

- `getText(String, Any...)`
- `getQuantityText(String, Int, Any...)`

The library will look for the matching values if there is a text containing the key (first parameter)
Format it if needed with the `Any...` *vararg* and will return it (or throw a `Resources.NotFoundException`).

For the plurals, use the 2nd argument with the *quantity* to determine the plural to use .

For example:

Given this `.po` file

```
#
msgid ""
msgstr ""
...
"Language: en_US\n"
"Plural-Forms: nplurals=5; plural=(n==0 ? 0 : n==1 ? 1 : n==2 ? 2 : 3);\n"

msgid "BACK"
msgstr "Previous"

msgid "YOU TAPPED TIMES"
msgstr "Counter: %d"

msgid "QUANTITY"
msgid_plural "QUANTITY"
msgstr[0] "item"
msgstr[1] "item"
msgstr[2] "items"
msgstr[3] "items"

msgid "YOU HAVE QUANTITY"
msgid_plural "YOU HAVE QUANTITY"
msgstr[0] "You have no bag"
msgstr[1] "You have one bag with %2$d items inside"
msgstr[2] "You have a pair of bag with %2$d items inside"
msgstr[3] "You have %1$d bags with %2$d items inside"
```

```kotlin
// Look for a text with the key "BACK"
Gero.getText("BACK")
// --> "Previous"

// Look for a text with the key "YOU TAPPED TIMES" and format it with 5
Gero.getText("YOU TAPPED TIMES", 5)
// --> Counter: 5

// Look for a plural text with the key "QUANTITY" and use the plural matching 10 in quantity
Gero.getQuantityText("QUANTITY", 10)
// --> items

// Look for a plural text with the key "YOU HAVE QUANTITY"
// Use the plural matching 0 in quantity
Gero.getQuantityText("YOU HAVE QUANTITY", 0)
// --> You have no bag

// Look for a plural text with the key "QUANTITY"
// Use the plural matching 2 in quantity
// Format the result with 2 arguments 2 and 120
Gero.getQuantityText("YOU HAVE QUANTITY", 2, 2, 120)
// --> You have a pair of bag with 120 items inside

// Look for a plural text with the key "QUANTITY"
// Use the plural matching 3 in quantity
// Format the result with 2 arguments 3 and 95
Gero.getQuantityText("YOU HAVE QUANTITY", 3, 3, 95)
// --> You have 3 of bag with 95 items inside
```

## Special thanks / 3rd part

In order to compute the plurals, we have to parse and understand the plurals header line
This line can be simple or complex

For example:
- `"Plural-Forms: nplurals=5; plural=(n==0 ? 0 : n==1 ? 1 : n==2 ? 2 : 3);\n"`
- `"Plural-Forms: nplurals=2; plural=(n != 1);\n"`
- `"Plural-Forms: nplurals=3; plural=n%10==1 && n%100!=11 ? 0 : n%10>=2 && n%10<=4 && (n%100<10 || n%100>=20) ? 1 : 2;"`

In order to keep it simple, we make use of [**Rhino**](https://github.com/mozilla/rhino) by  **mozilla** which is amazing.

If you add this library to your project, then  **Rhino** will be added, makes sure to use the right licence.

If you already use **Rhino** you can exclude it from the import.