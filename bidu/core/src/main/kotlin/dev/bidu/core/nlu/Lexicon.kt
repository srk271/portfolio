package dev.bidu.core.nlu

import java.util.Locale

/**
 * Word lists for the offline NLU. Every spelling variant (Hinglish, Devanagari, common typos) maps to one
 * canonical token; English words stay English. Keys are folded at build time, so Devanagari spellings
 * with or without nukta/chandrabindu land on the same entry.
 */
internal object Lexicon {

    private val canonical: Map<String, String> = buildMap {
        fun group(canon: String, vararg forms: String) {
            put(canon, canon)
            for (form in forms) put(TextFolding.fold(form).lowercase(Locale.ROOT), canon)
        }

        // Verbs.
        group("kar", "kr", "karo", "kro", "krdo", "kardo", "karde", "krde", "kardena", "karna", "krna", "kariye", "kijiye", "kijie",
            "karr", "karein", "karen", "kare", "kardijiye", "krdena", "karado", "karao", "karni", "karne", "करो", "कर", "करना",
            "कीजिए", "करें", "करिए", "करे", "करनी", "करने", "कर दो")
        group("khol", "kholo", "kholdo", "kholde", "kholna", "kholiye", "khool", "kholke", "khulo", "खोलो", "खोल", "खोलना", "खोलिए")
        group("open", "opn", "launch", "ओपन")
        group("chala", "chalao", "chalado", "चलाओ", "चला")
        group("chal", "chalo", "chalna", "chalen", "चलो", "चल", "चलना")
        group("bata", "batao", "btao", "bta", "batana", "batado", "batade", "bataiye", "batayein", "batayiye", "bolo", "bol",
            "sunao", "suna", "बताओ", "बता", "बताना", "बोलो", "सुनाओ", "बताइए")
        group("dila", "dilana", "dilao", "dilado", "dilade", "dilaana", "dilaiye", "dilaye", "दिलाना", "दिलाओ", "दिला", "दिलाइए")
        group("yaad", "yad", "याद")
        group("rakh", "rakho", "rakhna", "rakhlo", "rakhle", "rakhiye", "rakhana", "रख", "रखो", "रखना", "रखिए")
        group("laga", "lagao", "lagado", "lagade", "lagana", "lga", "lgao", "lagaiye", "lagaa", "लगा", "लगाओ", "लगाना", "लगाइए")
        group("likh", "likho", "likhdo", "likhde", "likhna", "likhiye", "likhlo", "likhle", "lik", "लिखो", "लिख", "लिखना", "लिखिए")
        group("dikha", "dikhao", "dikhado", "dikhana", "dikhaiye", "dikhade", "दिखाओ", "दिखा", "दिखाना")
        group("padh", "padho", "parh", "parho", "padhke", "पढो", "पढ", "पढ़ो")
        group("hata", "hatao", "hatado", "hatade", "hatana", "mita", "mitao", "mitado", "हटाओ", "हटा", "मिटाओ", "मिटा")
        group("delete", "del", "remove", "डिलीट")
        group("band", "bandh", "bnd", "बंद")
        group("bujha", "bujhao", "bujhado", "बुझाओ", "बुझा")
        group("jala", "jalao", "jalado", "जलाओ", "जला")
        group("chalu", "chaalu", "चालू")
        group("on", "ऑन", "ओन")
        group("off", "ऑफ", "ओफ")
        group("bhej", "bhejo", "bhejdo", "bhejna", "भेजो", "भेज")
        group("bula", "bulao", "bulado", "bulana", "bulaiye", "बुलाओ", "बुला")
        group("book", "buk", "bukk", "बुक")
        group("mila", "milao", "milado", "मिलाओ", "मिला")
        group("set", "sett", "सेट")
        group("bana", "banao", "banado", "बनाओ", "बना")
        group("daal", "daalo", "dalo", "डालो", "डाल")
        group("save", "सेव")
        group("dekh", "dekho", "dekhna", "dekhni", "dekhne", "देखो", "देख", "देखना", "देखनी")
        group("sun", "suno", "sunna", "sunno", "सुनो", "सुन", "सुनना")
        group("utha", "uthao", "uthana", "uthaana", "uthado", "uthade", "उठाओ", "उठा", "उठाना")
        group("jaga", "jagao", "jagana", "jagado", "जगाओ", "जगा", "जगाना")
        group("ruk", "ruko", "rukja", "rukjao", "रुको", "रुक")
        group("cancel", "cancle", "kancel", "कैंसल", "कैंसिल")
        group("chhod", "chhodo", "chod", "chodo", "chhoddo", "छोडो", "छोड")
        group("rehne", "rehnede", "rhne", "rahne", "रहने")
        group("stop", "स्टॉप")
        group("bas", "बस")
        group("so", "soja", "sojao", "सो")
        group("ja", "jao", "jaa", "जा", "जाओ")
        group("ho", "हो")
        group("gaya", "gya", "gayi", "gaye", "gye", "गया", "गयी", "गई", "गए")
        group("sakta", "sakte", "sakti", "sakoge", "sakogi", "सकते", "सकता", "सकती")
        group("rehta", "rehti", "rehte", "rahta", "rahti", "रहता", "रहती", "रहते")
        group("chahiye", "chaiye", "chahie", "चाहिए")
        group("bhool", "bhul", "bhoolja", "bhooljao", "bhulja", "bhoolna", "bhulna", "भूल", "भूलो", "भूलना", "भूलजा")
        group("bhula", "bhulao", "bhulado", "bhulade", "भुला", "भुलाओ")
        group("matlab", "mtlb", "yaani", "yani", "मतलब", "यानी")
        group("forget", "4get")
        // Function words.
        group("ko", "को")
        group("ka", "का")
        group("ki", "की")
        group("ke", "के")
        group("se", "से")
        group("ne", "ने")
        group("mein", "mei", "में")
        group("pe", "par", "pr", "पे", "पर")
        group("tak", "तक")
        group("liye", "lie", "liyee", "लिए", "लिये")
        group("wala", "wali", "wale", "vala", "vali", "vale", "waala", "waali", "वाला", "वाली", "वाले")
        group("baare", "bare", "baarey", "बारे")
        group("baad", "baadh", "बाद")
        group("hai", "है")
        group("hain", "हैं")
        group("tha", "thi", "था", "थी")
        group("kya", "kia", "kyaa", "क्या")
        group("kitna", "kitne", "kitni", "kitney", "kitnaa", "कितना", "कितने", "कितनी")
        group("kahan", "kahaan", "kaha", "kidhar", "कहां", "कहा")
        group("kaun", "kon", "कौन")
        group("kaunsa", "konsa", "kaunsi", "konsi", "kaunse", "konse", "कौनसा", "कौनसी", "कौनसे")
        group("aur", "और")
        group("mera", "meri", "mere", "mra", "mri", "mre", "मेरा", "मेरी", "मेरे")
        group("mujhe", "mujhko", "mjhe", "muje", "मुझे", "मुझको")
        group("apna", "apni", "apne", "अपना", "अपनी", "अपने")
        group("main", "मैं")
        group("ye", "yeh", "ये", "यह")
        group("log", "logo", "logon", "लोग", "लोगों")
        // Time words.
        group("baje", "bje", "baja", "bja", "bajey", "baj", "बजे", "बजा", "बज")
        group("bajkar", "bajke", "बजकर")
        group("time", "taim", "टाइम")
        group("samay", "samai", "समय")
        group("waqt", "wakt", "vakt", "vaqt", "वक्त")
        group("date", "डेट")
        group("tareekh", "tarikh", "tarik", "tareek", "तारीख")
        group("din", "दिन")
        group("aaj", "aj", "आज")
        group("kal", "कल")
        group("parso", "parson", "parsoon", "parsho", "parsu", "परसों", "परसो")
        group("narso", "narson", "नरसों")
        group("subah", "subha", "subeh", "savere", "sawere", "सुबह", "सवेरे")
        group("dopahar", "dopehar", "dupahar", "dophar", "dopher", "duphar", "दोपहर")
        group("shaam", "sham", "saam", "शाम")
        group("raat", "rat", "रात")
        group("abhi", "abi", "अभी")
        group("minute", "minutes", "min", "mins", "minat", "mint", "minit", "mnt", "मिनट", "मिनिट")
        group("second", "seconds", "sec", "secs", "sekand", "sekend", "सेकंड", "सेकेंड", "सेकण्ड")
        group("ghanta", "ghante", "ghanton", "ghantey", "ghnta", "ghnte", "घंटा", "घंटे", "घंटों")
        group("hour", "hours", "hr", "hrs")
        group("day", "days")
        group("tomorrow", "tmrw", "tomorow", "tommorow", "tommorrow", "tmr")
        group("tonight", "tonite")
        group("monday", "somvar", "somwar", "सोमवार")
        group("tuesday", "mangalvar", "mangalwar", "मंगलवार")
        group("wednesday", "budhvar", "budhwar", "बुधवार")
        group("thursday", "guruvar", "guruwar", "brihaspativar", "गुरुवार", "बृहस्पतिवार")
        group("friday", "shukravar", "shukrawar", "शुक्रवार")
        group("saturday", "shanivar", "shaniwar", "शनिवार")
        group("sunday", "ravivar", "raviwar", "itvaar", "itwar", "etvaar", "रविवार", "इतवार")
        // Numbers.
        group("ek", "एक")
        group("do", "दो")
        group("teen", "tin", "तीन")
        group("char", "chaar", "चार")
        group("paanch", "panch", "paach", "पांच")
        group("chhe", "chhah", "chah", "che", "chha", "छह", "छः", "छे")
        group("saat", "सात")
        group("aath", "ath", "आठ")
        group("nau", "नौ")
        group("das", "dus", "दस")
        group("gyarah", "gyara", "gyaarah", "ग्यारह")
        group("barah", "bara", "baarah", "baara", "बारह")
        group("terah", "तेरह")
        group("chaudah", "chauda", "चौदह")
        group("pandrah", "pandra", "पंद्रह", "पन्द्रह")
        group("bees", "bis", "बीस")
        group("pachchees", "pachees", "pachis", "पच्चीस")
        group("tees", "tis", "तीस")
        group("chalees", "chalis", "चालीस")
        group("paintalees", "paintalis", "पैंतालीस")
        group("pachaas", "pachas", "पचास")
        group("dedh", "derh", "dhed", "डेढ")
        group("dhai", "dhaai", "dhaee", "ढाई")
        group("aadha", "adha", "aadhe", "adhe", "aadhi", "adhi", "आधा", "आधे", "आधी")
        group("saadhe", "sadhe", "saade", "sade", "saadhey", "साढे")
        group("sava", "sawa", "सवा")
        group("paune", "pone", "पौने")
        // Nouns.
        group("note", "notes", "nots", "नोट", "नोट्स")
        group("reminder", "reminders", "remainder", "remainders", "reminde", "रिमाइंडर", "रिमाइंडर्स")
        group("remind", "remaind", "रिमाइंड")
        group("alarm", "alarms", "alaram", "allarm", "अलार्म")
        group("timer", "timers", "taimar", "टाइमर")
        group("torch", "tourch", "टॉर्च", "टार्च", "टोर्च")
        group("flashlight", "flashlite", "फ्लैशलाइट")
        group("battery", "battry", "betri", "batery", "battary", "बैटरी")
        group("charge", "चार्ज")
        group("google", "gugal", "googal", "गूगल")
        group("search", "serch", "sarch", "सर्च")
        group("khoj", "khojo", "खोजो", "खोज")
        group("dhundh", "dhundho", "dhoondh", "dhoondho", "dhundo", "dhund", "ढूंढो", "ढूंढ", "ढूंढना")
        group("cab", "cabs", "कैब")
        group("taxi", "texi", "टैक्सी")
        group("auto", "ऑटो")
        group("uber", "ubar", "ऊबर", "उबर")
        group("ola", "ओला")
        group("rapido", "रैपिडो")
        group("ghar", "ghr", "घर")
        group("office", "ofis", "offc", "ofice", "ऑफिस", "ओफिस", "daftar", "दफ्तर")
        group("jaana", "jana", "jaane", "jane", "jaani", "जाना", "जाने")
        group("movie", "movies", "film", "films", "filam", "picture", "pikchar", "फिल्म", "मूवी", "पिक्चर")
        group("ticket", "tickets", "tikat", "tiket", "tikit", "टिकट", "टिकटें", "टिकिट")
        group("seat", "seats", "सीट", "सीटें")
        group("cinema", "sinema", "theatre", "theater", "थिएटर", "सिनेमा")
        group("show", "shows", "शो")
        group("call", "kol", "कॉल")
        group("phone", "fone", "phon", "फोन")
        group("dial", "डायल")
        group("number", "nmbr", "नंबर")
        group("contact", "contacts", "कांटेक्ट", "कॉन्टैक्ट")
        group("message", "msg", "messages", "मैसेज")
        group("naam", "nam", "नाम")
        group("pasand", "psnd", "पसंद")
        group("address", "adress", "addres", "एड्रेस")
        group("city", "shehar", "sheher", "shahar", "शहर")
        group("madad", "मदद")
        group("mic", "microphone", "माइक")
        // Answers and fillers.
        group("haan", "han", "haa", "ha", "haanji", "hanji", "हां", "हा")
        group("nahi", "nahin", "nai", "nhi", "nahee", "नहीं", "नही")
        group("na", "ना")
        group("mat", "मत")
        group("theek", "thik", "thek", "ठीक")
        group("sahi", "सही")
        group("bilkul", "बिल्कुल", "बिलकुल")
        group("ok", "okay", "okk", "okey", "ओके")
        group("bhai", "bhaii", "भाई")
        group("bhaiya", "bhaiyya", "bhaya", "भैया")
        group("yaar", "yar", "yr", "यार")
        group("please", "pls", "plz", "plis", "plij", "pliz", "kripya", "कृपया", "प्लीज")
        group("zara", "jara", "जरा")
        group("jaldi", "जल्दी")
        group("ji", "जी")
        // Family words, romanised so memory aliases (contact_alias.mummy) match Devanagari speech.
        group("mummy", "मम्मी")
        group("mumma", "मम्मा")
        group("maa", "मां", "माता")
        group("papa", "पापा")
        group("pitaji", "पिताजी")
        group("didi", "दीदी")
        group("dadi", "दादी")
        group("dada", "दादा")
        group("nani", "नानी")
        group("nana", "नाना")
        group("chacha", "चाचा")
        group("behen", "बहन")
        // English variants collapsed to one spelling.
        group("what", "whats", "wat")
        group("hey", "हे")
    }

    fun canon(word: String): String = canonical[word] ?: word

    private val NUMBERS: Map<String, Double> = mapOf(
        "ek" to 1.0, "do" to 2.0, "teen" to 3.0, "char" to 4.0, "paanch" to 5.0, "chhe" to 6.0, "saat" to 7.0, "aath" to 8.0,
        "nau" to 9.0, "das" to 10.0, "gyarah" to 11.0, "barah" to 12.0, "terah" to 13.0, "chaudah" to 14.0, "pandrah" to 15.0,
        "bees" to 20.0, "pachchees" to 25.0, "tees" to 30.0, "chalees" to 40.0, "paintalees" to 45.0, "pachaas" to 50.0,
        "dedh" to 1.5, "dhai" to 2.5, "aadha" to 0.5,
        "one" to 1.0, "two" to 2.0, "three" to 3.0, "four" to 4.0, "five" to 5.0, "six" to 6.0, "seven" to 7.0,
        "eight" to 8.0, "nine" to 9.0, "ten" to 10.0, "eleven" to 11.0, "twelve" to 12.0, "fifteen" to 15.0,
        "twenty" to 20.0, "thirty" to 30.0, "forty" to 40.0, "fifty" to 50.0, "half" to 0.5,
    )
    private val ENGLISH_TENS = setOf("twenty", "thirty", "forty", "fifty")
    private val ENGLISH_UNITS = setOf("one", "two", "three", "four", "five", "six", "seven", "eight", "nine")
    private val MODIFIERS = mapOf("saadhe" to 0.5, "sava" to 0.25, "paune" to -0.25)

    fun number(canon: String): Double? = NUMBERS[canon]
    fun modifier(canon: String): Double? = MODIFIERS[canon]
    fun isEnglishTens(canon: String) = canon in ENGLISH_TENS
    fun isEnglishUnit(canon: String) = canon in ENGLISH_UNITS

    val UNITS = setOf("second", "minute", "ghanta", "hour", "din", "day")
    val COUNTED_NOUNS = UNITS + setOf("ticket", "seat", "log", "people", "person", "persons", "baje", "baar", "times")
    val POSTPOSITIONS = setOf("ko", "ka", "ki", "ke", "se", "ne")
    val POSSESSIVES = setOf("mera", "apna", "my", "our")
    val FILLERS = setOf(
        "bhai", "bhaiya", "bro", "brother", "yaar", "dost", "boss", "please", "zara", "re", "arre", "oye", "ji", "jaldi",
        "sir", "kindly", "buddy", "dude", "bhaisaab",
    )
    val KINSHIP_FILLERS = setOf("bhai", "bhaiya", "bro", "brother", "yaar", "dost", "boss")
    val AUXILIARIES = setOf("do", "de", "dena", "dijiye", "dijie", "dijiyega", "dein", "diya", "dete", "lo", "le", "lena", "lijiye", "na", "naa")
    val VERBS = setOf(
        "kar", "khol", "open", "chala", "bata", "dila", "rakh", "laga", "likh", "dikha", "padh", "hata", "delete", "band", "bujha",
        "jala", "chalu", "on", "off", "bhej", "bula", "book", "call", "dial", "ruk", "cancel", "chhod", "rehne", "sun", "khoj",
        "dhundh", "search", "google", "set", "start", "mila", "bana", "daal", "add", "save", "dekh", "utha", "jaga", "stop",
    )

    val HINDI_MARKERS = setOf(
        "kar", "khol", "bata", "dila", "yaad", "rakh", "laga", "likh", "dikha", "padh", "hata", "band", "bujha", "jala", "chalu",
        "bhej", "bula", "ruk", "chhod", "rehne", "hai", "hain", "kya", "kitna", "ko", "ka", "ki", "ke", "se", "mein", "pe", "tak",
        "liye", "wala", "baje", "kal", "aaj", "parso", "subah", "dopahar", "shaam", "raat", "abhi", "mera", "mujhe", "apna",
        "ghar", "haan", "nahi", "mat", "theek", "bhai", "yaar", "ghanta", "baad", "jaana", "chahiye", "kaunsa", "kahan", "kaun",
        "naam", "pasand", "madad", "samay", "waqt", "tareekh", "din", "dekh", "utha", "jaga", "mila", "bana", "daal", "chal",
        "chala", "zara", "aur", "tha", "baare", "sakta", "rehta", "dila", "bajkar",
    )
    val ENGLISH_MARKERS = setOf(
        "the", "is", "what", "please", "set", "open", "me", "my", "for", "to", "a", "an", "remind", "tell", "how", "much", "of",
        "in", "at", "on", "turn", "show", "find", "today", "tomorrow", "tonight", "can", "you", "i", "it", "and", "from", "after",
        "this", "next", "take", "make", "write", "down", "add", "are", "am", "where", "who", "which", "when", "morning",
        "evening", "night", "afternoon", "like", "love", "name", "home", "work", "stop", "listening", "wake", "up", "call",
        "search", "book", "minute", "hour", "second", "timer", "alarm", "time", "date", "battery", "note", "reminder", "delete",
    )
}
