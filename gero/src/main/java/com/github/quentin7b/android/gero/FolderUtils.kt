package com.github.quentin7b.android.gero

import android.content.Context
import android.util.Log
import kotlinx.coroutines.*
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * List all the files under the asset/$path folder, and returns a list of all file paths.
 * Those files are candidate for translation files
 *
 * @param context an Android Context, used to access the assets
 * @param path the subfolder under `assets` where the po files are contained
 * @return a list of file paths
 */
private fun listFileUnderAssetFolder(context: Context, path: String): List<String> {
    val filesPaths = mutableListOf<String>()
    Log.d("Gero", "listFileUnderAssetFolder - Looking for files under $path")
    try {
        val itemsInFolder = context.assets.list(path)!!.toMutableList()
        // Not empty means it contains files
        if (itemsInFolder.isNotEmpty()) {
            Log.d("Gero", "listFileUnderAssetFolder - Looks like $path is not empty")
            itemsInFolder.forEach {
                filesPaths.addAll(listFileUnderAssetFolder(context, "$path/$it"))
            }
        } else {
            Log.d("Gero", "listFileUnderAssetFolder - Looks like $path is a file")
            // No content, means it is a file, register if it is a po file
            if (path.endsWith(".po")) {
                filesPaths.add(path)
            }
        }
    } catch (err: Exception) {
        // Not a file
    }
    return filesPaths.toList()
}

/**
 * For each po file under the assets/$path folder, it will detect if it is a po file, and if so.
 * It will check what is the supported language for this file and register it in `fileLocales`
 * @see listFileUnderAssetFolder used to list the file candidates
 *
 * @param context an Android Context, used to access the assets
 * @param path the subfolder under `assets` where the po files are contained, by default its value is `po`
 * @return a deferred completed or exception failed
 */
internal fun registerTranslationFilesAsync(
    context: Context,
    path: String = "po"
): Deferred<HashMap<String, List<String>>> {
    val deferred = CompletableDeferred<HashMap<String, List<String>>>()
    val fileLocales = HashMap<String, MutableList<String>>()
    CoroutineScope(Job()).launch(Dispatchers.IO) {
        // 1. First of all, list all files under the asset/$rootPath folder
        val filesPaths = listFileUnderAssetFolder(context, path)
        Log.d(
            "Gero",
            "registerTranslationFilesAsync - The list of translation files candidates under $path is $filesPaths"
        )
        // 2. Then open all files to look at the Language line
        var reader: BufferedReader? = null
        var inputStreamReader: InputStreamReader? = null
        try {
            filesPaths.forEach { filePath ->
                Log.d("Gero", "registerTranslationFilesAsync - Exploring $filePath")
                inputStreamReader =
                    InputStreamReader(context.assets.open(filePath), "UTF-8")
                reader = BufferedReader(inputStreamReader)

                val iterator = reader!!.lineSequence().iterator()
                while (iterator.hasNext()) {
                    val line = iterator.next()
                    if (line.startsWith("\"Language:")) {
                        // This is it
                        val lTag = line.substring(line.indexOf(":") + 1, line.length - 1)
                            .replace(" ", "")
                            .replace("\\n", "")
                        Log.d(
                            "Gero",
                            "registerTranslationFilesAsync - Found Language $lTag in $filePath"
                        )
                        if (!fileLocales.containsKey(lTag)) {
                            fileLocales[lTag] = mutableListOf()
                        }
                        fileLocales[lTag]!!.add(filePath)
                        break
                    }
                }
            }
            val readOnlyMap = HashMap<String, List<String>>()
            fileLocales.forEach {
                readOnlyMap[it.key] = it.value.toList()
            }
            deferred.complete(readOnlyMap)
        } catch (err: Exception) {
            deferred.completeExceptionally(err)
        } finally {
            inputStreamReader?.close()
            reader?.close()
        }
    }
    return deferred
}