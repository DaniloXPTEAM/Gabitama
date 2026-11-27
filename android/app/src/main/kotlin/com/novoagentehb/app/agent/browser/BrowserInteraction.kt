package com.novoagentehb.app.agent.browser

/**
 * Interação com elementos da página (clicar e preencher campo) pelo navegador embutido.
 *
 * Implementação própria do GabiTama (licença MIT). Toda interação é uma ação externa:
 * a PolicyGate exige confirmação explícita da pessoa antes de chegar aqui.
 *
 * Regras:
 *  - o agente envia apenas o seletor CSS e, no preenchimento, o texto;
 *  - o script executado no WebView é fixo; seletor e valor entram como literais JS escapados;
 *  - nunca envia formulário (sem form.submit()): enviar exige clicar no botão, que é uma ação separada;
 *  - senha, arquivo e campos ocultos são recusados;
 *  - sucesso de preenchimento só é informado se o WebView relê o mesmo valor.
 */
enum class BrowserInteractionKind(val wireName: String) {
    CLICK("click"),
    FILL("fill");

    companion object {
        fun fromWireName(name: String): BrowserInteractionKind? =
            entries.find { it.wireName == name.trim().lowercase() }
    }
}

data class BrowserInteractionRequest(
    val kind: BrowserInteractionKind,
    val selector: String,
    val value: String? = null,
) {
    companion object {
        const val MAX_SELECTOR_CHARS = 200
        const val MAX_VALUE_CHARS = 2000

        /** Valida a solicitação antes de qualquer contato com o WebView. */
        fun validate(kind: BrowserInteractionKind, rawSelector: String, rawValue: String?): Result<BrowserInteractionRequest> {
            val selector = rawSelector.trim()
            if (selector.isEmpty()) return Result.failure(IllegalArgumentException("Seletor vazio"))
            if (selector.length > MAX_SELECTOR_CHARS) return Result.failure(IllegalArgumentException("Seletor longo demais"))
            if (selector.any { it.isISOControl() }) return Result.failure(IllegalArgumentException("Seletor com caractere de controle"))
            if (kind == BrowserInteractionKind.FILL) {
                if (rawValue == null) return Result.failure(IllegalArgumentException("Falta o texto a preencher (value)"))
                if (rawValue.length > MAX_VALUE_CHARS) return Result.failure(IllegalArgumentException("Texto longo demais"))
                if (rawValue.any { it.isISOControl() && it != '\n' && it != '\t' }) {
                    return Result.failure(IllegalArgumentException("Texto com caractere de controle"))
                }
                return Result.success(BrowserInteractionRequest(kind, selector, rawValue))
            }
            return Result.success(BrowserInteractionRequest(kind, selector, null))
        }
    }
}

/** Literais JavaScript seguros: escapam aspas, barras, quebras de linha e caracteres de controle. */
object BrowserJs {
    fun stringLiteral(value: String): String {
        val sb = StringBuilder(value.length + 2)
        sb.append('"')
        for (c in value) {
            when (c) {
                '\\' -> sb.append("\\\\")
                '"' -> sb.append("\\\"")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                '<' -> sb.append("\\u003c")
                '>' -> sb.append("\\u003e")
                '\u2028' -> sb.append("\\u2028")
                '\u2029' -> sb.append("\\u2029")
                else -> if (c.code < 0x20) sb.append(String.format("\\u%04x", c.code)) else sb.append(c)
            }
        }
        sb.append('"')
        return sb.toString()
    }
}

/** Scripts fixos. Os únicos dados variáveis são literais escapados por [BrowserJs]. */
object BrowserInteractionScripts {
    private const val FIND = "var el;try{el=document.querySelector(s);}catch(e){return 'bad_selector';}" +
        "if(!el)return 'not_found';" +
        "var t=(el.type||'').toLowerCase();" +
        "if(t==='password'||t==='file'||t==='hidden')return 'forbidden';" +
        "var r=el.getBoundingClientRect();" +
        "if(!r||(r.width===0&&r.height===0))return 'not_visible';"

    fun build(request: BrowserInteractionRequest): String {
        val sel = BrowserJs.stringLiteral(request.selector)
        return when (request.kind) {
            BrowserInteractionKind.CLICK ->
                "(function(){var s=$sel;$FIND" +
                    "if(el.disabled)return 'disabled';" +
                    "el.click();return 'ok';})()"
            BrowserInteractionKind.FILL -> {
                val value = BrowserJs.stringLiteral(request.value.orEmpty())
                "(function(){var s=$sel;var v=$value;$FIND" +
                    "if(el.disabled||el.readOnly)return 'disabled';" +
                    "var tag=el.tagName;var ty=(el.type||'text').toLowerCase();" +
                    "var typeOk=tag==='TEXTAREA'||(tag==='INPUT'&&['text','search','email','url','tel','number'].indexOf(ty)>=0);" +
                    "if(!typeOk)return 'fill_unsupported';" +
                    "el.focus();" +
                    "var proto=tag==='TEXTAREA'?HTMLTextAreaElement.prototype:HTMLInputElement.prototype;" +
                    "Object.getOwnPropertyDescriptor(proto,'value').set.call(el,v);" +
                    "el.dispatchEvent(new Event('input',{bubbles:true}));" +
                    "el.dispatchEvent(new Event('change',{bubbles:true}));" +
                    "return el.value===v?'ok':'not_verified';})()"
            }
        }
    }
}

/** Interpreta o retorno de evaluateJavascript (string JSON com o código do script). */
object BrowserInteractionCodec {
    fun parseCode(raw: String?): String {
        if (raw.isNullOrEmpty() || raw == "null") return "no_response"
        return raw.removeSurrounding("\"")
    }

    /** Converte o código do script em resultado para o agente. Sucesso só com código 'ok'. */
    fun toResult(kind: BrowserInteractionKind, code: String, pageUrl: String?): BrowserActionResult = when (code) {
        "ok" -> when (kind) {
            BrowserInteractionKind.FILL -> BrowserActionResult.ok("Preenchi o campo e conferi o valor na página.", pageUrl)
            BrowserInteractionKind.CLICK -> BrowserActionResult.ok(
                "Cliquei no elemento. A página pode ter mudado: confira com get_text ou get_state antes de afirmar o resultado.",
                pageUrl,
            )
        }
        "bad_selector" -> BrowserActionResult.error("SELECTOR_INVALID", "Seletor CSS inválido.", pageUrl)
        "not_found" -> BrowserActionResult.error("ELEMENT_NOT_FOUND", "Nenhum elemento encontrado para o seletor.", pageUrl)
        "not_visible" -> BrowserActionResult.error("ELEMENT_NOT_VISIBLE", "O elemento existe, mas não está visível.", pageUrl)
        "disabled" -> BrowserActionResult.error("ELEMENT_DISABLED", "O elemento está desativado ou somente leitura.", pageUrl)
        "forbidden" -> BrowserActionResult.error("ELEMENT_FORBIDDEN", "Não interajo com senha, arquivo ou campo oculto.", pageUrl)
        "fill_unsupported" -> BrowserActionResult.error("FILL_UNSUPPORTED", "Este tipo de campo não aceita preenchimento automático.", pageUrl)
        "not_verified" -> BrowserActionResult.error("FILL_NOT_VERIFIED", "O campo não manteve o valor esperado; não confirmei o preenchimento.", pageUrl)
        else -> BrowserActionResult.error("INTERACTION_NO_RESPONSE", "O navegador não confirmou a interação.", pageUrl)
    }
}
