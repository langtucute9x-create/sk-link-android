package vn.sklight.app

/**
 * Tên hãng / dòng xe do hộp gửi về là tiếng Trung. Ở đây đổi sang tên quốc tế (chữ Latin) để đọc được.
 * Chỉ đổi cách hiện; mã hãng / dòng gửi cho hộp vẫn lấy nguyên từ danh sách của hộp. Từ nào chưa có trong bảng thì giữ nguyên chữ gốc.
 */
object CarNames {
    private val NAMES = listOf(
        // hãng
        "大众" to "Volkswagen", "丰田" to "Toyota", "本田" to "Honda", "日产" to "Nissan", "现代" to "Hyundai", "起亚" to "Kia", "福特" to "Ford",
        "别克" to "Buick", "雪佛兰" to "Chevrolet", "凯迪拉克" to "Cadillac", "马自达" to "Mazda", "三菱" to "Mitsubishi", "斯巴鲁" to "Subaru",
        "铃木" to "Suzuki", "雷克萨斯" to "Lexus", "英菲尼迪" to "Infiniti", "讴歌" to "Acura", "奥迪" to "Audi", "宝马" to "BMW", "奔驰" to "Mercedes-Benz",
        "斯柯达" to "Skoda", "保时捷" to "Porsche", "沃尔沃" to "Volvo", "路虎" to "Land Rover", "捷豹" to "Jaguar", "标致" to "Peugeot", "雪铁龙" to "Citroen",
        "雷诺" to "Renault", "吉普" to "Jeep", "道奇" to "Dodge", "林肯" to "Lincoln", "特斯拉" to "Tesla", "比亚迪" to "BYD", "吉利" to "Geely",
        "长城" to "Great Wall", "哈弗" to "Haval", "长安" to "Changan", "奇瑞" to "Chery", "名爵" to "MG", "荣威" to "Roewe", "五菱" to "Wuling",
        "宝骏" to "Baojun", "红旗" to "Hongqi", "领克" to "Lynk & Co", "传祺" to "Trumpchi", "广汽" to "GAC", "一汽" to "FAW", "东风" to "Dongfeng",
        "上汽" to "SAIC", "北汽" to "BAIC", "江淮" to "JAC", "理想" to "Li Auto", "蔚来" to "NIO", "小鹏" to "XPeng", "坦克" to "Tank", "欧拉" to "ORA",
        "捷途" to "Jetour", "星途" to "Exeed",
        // Toyota
        "RAV4荣放" to "RAV4", "荣放RAV4" to "RAV4", "亚洲龙" to "Avalon", "亚洲狮" to "Allion", "凯美瑞" to "Camry", "卡罗拉锐放" to "Corolla Cross", "卡罗拉" to "Corolla", "雷凌" to "Levin", "凌尚" to "Levin GT",
        "汉兰达" to "Highlander", "皇冠陆放" to "Crown Kluger", "皇冠" to "Crown", "荣放" to "RAV4", "威兰达" to "Wildlander", "锋兰达" to "Frontlander",
        "赛那" to "Sienna", "塞纳" to "Sienna", "格瑞维亚" to "Granvia", "普拉多" to "Prado", "霸道" to "Prado", "兰德酷路泽" to "Land Cruiser", "陆地巡洋舰" to "Land Cruiser",
        "埃尔法" to "Alphard", "威尔法" to "Vellfire", "威驰" to "Vios", "致炫" to "Yaris L", "致享" to "Yaris L Sedan", "雅力士" to "Yaris", "奕泽" to "IZOA",
        "锐志" to "Reiz", "海狮" to "Hiace", "考斯特" to "Coaster", "柯斯达" to "Coaster", "普锐斯" to "Prius", "凌放" to "Harrier", "红杉" to "Sequoia", "坦途" to "Tundra",
        // Honda
        "雅阁" to "Accord", "思域" to "Civic", "型格" to "Integra", "皓影" to "Breeze", "奥德赛" to "Odyssey", "艾力绅" to "Elysion", "飞度" to "Fit", "缤智" to "Vezel",
        "冠道" to "Avancier", "英仕派" to "Inspire", "凌派" to "Crider", "享域" to "Envix", "锋范" to "City", "哥瑞" to "Greiz", "竞瑞" to "Gienia", "杰德" to "Jade",
        "思铂睿" to "Spirior", "歌诗图" to "Crosstour", "致在" to "ZR-V", "来福酱" to "Life",
        // Nissan
        "轩逸" to "Sylphy", "天籁" to "Teana", "奇骏" to "X-Trail", "逍客" to "Qashqai", "途达" to "Terra", "楼兰" to "Murano", "劲客" to "Kicks", "骐达" to "Tiida",
        "蓝鸟" to "Lannia", "西玛" to "Maxima", "阳光" to "Sunny", "途乐" to "Patrol", "纳瓦拉" to "Navara", "艾睿雅" to "Ariya", "探陆" to "Pathfinder", "骊威" to "Livina",
        "玛驰" to "March", "贵士" to "Quest",
        // Volkswagen
        "速腾" to "Sagitar", "迈腾" to "Magotan", "帕萨特" to "Passat", "朗逸" to "Lavida", "朗行" to "Gran Lavida", "朗境" to "Cross Lavida", "宝来" to "Bora",
        "高尔夫" to "Golf", "途观" to "Tiguan", "探岳" to "Tayron", "途昂" to "Teramont", "探歌" to "T-Roc", "途岳" to "Tharu", "凌渡" to "Lamando", "揽境" to "Talagon",
        "揽巡" to "Tavendor", "威然" to "Viloran", "途铠" to "T-Cross", "探影" to "Tacqua", "桑塔纳" to "Santana", "捷达" to "Jetta", "辉昂" to "Phideon", "途锐" to "Touareg",
        "夏朗" to "Sharan", "途安" to "Touran", "蔚领" to "C-Trek", "甲壳虫" to "Beetle", "尚酷" to "Scirocco", "迈特威" to "Multivan", "凯路威" to "Caravelle", "波罗" to "Polo",
        "明锐" to "Octavia", "速派" to "Superb", "柯迪亚克" to "Kodiaq", "柯珞克" to "Karoq", "柯米克" to "Kamiq", "昕锐" to "Rapid", "昕动" to "Rapid Spaceback",
        // Hyundai, Kia
        "伊兰特" to "Elantra", "领动" to "Elantra (Lingdong)", "悦动" to "Elantra (Yuedong)", "朗动" to "Elantra (Langdong)", "索纳塔" to "Sonata", "途胜" to "Tucson",
        "胜达" to "Santa Fe", "名图" to "Mistra", "库斯途" to "Custo", "菲斯塔" to "Lafesta", "昂希诺" to "Encino", "瑞纳" to "Verna", "悦纳" to "Verna", "帕里斯帝" to "Palisade",
        "沐飒" to "Mufasa", "智跑" to "Sportage", "狮铂拓界" to "Sportage", "狮铂" to "Sportage", "索兰托" to "Sorento", "嘉华" to "Carnival", "奕跑" to "KX1", "傲跑" to "KX3",
        "赛图斯" to "Seltos", "福瑞迪" to "Forte", "凯酷" to "K5", "焕驰" to "Pegas",
        // Ford, Lincoln
        "福克斯" to "Focus", "蒙迪欧" to "Mondeo", "锐界" to "Edge", "探险者" to "Explorer", "翼虎" to "Kuga", "锐际" to "Escape", "领界" to "Territory", "福睿斯" to "Escort",
        "金牛座" to "Taurus", "野马" to "Mustang", "翼搏" to "EcoSport", "嘉年华" to "Fiesta", "撼路者" to "Everest", "途睿欧" to "Tourneo", "全顺" to "Transit", "游骑侠" to "Ranger",
        "领睿" to "Equator Sport", "领裕" to "Equator", "烈马" to "Bronco", "征服者" to "Expedition", "猛禽" to "Raptor", "电马" to "Mustang Mach-E",
        "领航员" to "Navigator", "飞行家" to "Aviator", "航海家" to "Nautilus", "冒险家" to "Corsair",
        // GM
        "通用" to "GM", "君威" to "Regal", "君越" to "LaCrosse", "昂科威" to "Envision", "昂科拉" to "Encore", "昂科旗" to "Enclave", "英朗" to "Excelle GT", "威朗" to "Verano",
        "凯越" to "Excelle", "阅朗" to "Excelle GX", "微蓝" to "Velite", "迈锐宝" to "Malibu", "科鲁泽" to "Monza", "科鲁兹" to "Cruze", "探界者" to "Equinox", "开拓者" to "Blazer",
        "创酷" to "Trax", "创界" to "Trailblazer", "科沃兹" to "Cavalier", "赛欧" to "Sail", "科帕奇" to "Captiva", "沃兰多" to "Orlando", "星迈罗" to "Seeker", "畅巡" to "Menlo",
        "库罗德" to "Colorado", "索罗德" to "Silverado", "太浩" to "Tahoe", "萨博班" to "Suburban", "凯雷德" to "Escalade", "锐歌" to "Lyriq",
        // Mazda, Mitsubishi
        "阿特兹" to "Atenza", "昂克赛拉" to "Axela", "欧蓝德" to "Outlander", "帕杰罗" to "Pajero", "劲炫" to "ASX", "奕歌" to "Eclipse Cross"
    ).sortedByDescending { it.first.length }

    // từ phụ: (tiếng Trung, tiếng Việt, tiếng Anh)
    private val WORDS = listOf(
        Triple("新能源", "xe điện hóa", "NEV"), Triple("插电混动", "hybrid sạc ngoài", "plug-in hybrid"), Triple("插电", "sạc ngoài", "plug-in"),
        Triple("油电混动", "hybrid", "hybrid"), Triple("混动", "hybrid", "hybrid"), Triple("双擎", "hybrid", "hybrid"), Triple("纯电", "điện", "EV"), Triple("电动", "điện", "EV"),
        Triple("两厢", "hatchback", "hatchback"), Triple("三厢", "sedan", "sedan"), Triple("顶配", "bản cao nhất", "top trim"), Triple("高配", "bản cao", "high trim"),
        Triple("中配", "bản giữa", "mid trim"), Triple("低配", "bản thấp", "low trim"), Triple("进口", "nhập khẩu", "imported"), Triple("全系", "mọi bản", "all trims"),
        Triple("系列", "", ""), Triple("其他", "khác", "other"), Triple("其它", "khác", "other"), Triple("以后", "trở đi", "onwards"), Triple("之后", "trở đi", "onwards"),
        Triple("以前", "trở về trước", "and earlier"), Triple("之前", "trở về trước", "and earlier"), Triple("新款", "đời mới", "new"), Triple("老款", "đời cũ", "old"),
        Triple("不带", "không có", "without"), Triple("带", "có", "with"), Triple("原车", "nguyên bản", "factory"), Triple("氛围灯", "đèn viền", "ambient light"),
        Triple("液晶仪表", "đồng hồ LCD", "LCD cluster"), Triple("仪表", "đồng hồ", "cluster"), Triple("大屏", "màn hình lớn", "large screen"), Triple("小屏", "màn hình nhỏ", "small screen"),
        Triple("、", ", ", ", "), Triple("，", ", ", ", "), Triple("（", " (", " ("), Triple("）", ") ", ") ")
    )
    private val NUM = mapOf("十二" to 12, "十一" to 11, "十" to 10, "九" to 9, "八" to 8, "七" to 7, "六" to 6, "五" to 5, "四" to 4, "三" to 3, "二" to 2, "一" to 1)
    private val cache = HashMap<String, String>()

    fun tr(src: String): String {
        if (src.isEmpty()) return src
        val vi = L.cur.intValue == 0
        val key = (if (vi) "v" else "e") + src
        cache[key]?.let { return it }
        var s = src
        var year = ""
        // đời xe: "21款", "18-22款", "2021款", có thể kèm "后" (trở đi)
        Regex("(\\d{2,4})(?:\\s*[-~到至]\\s*(\\d{2,4}))?款(后|前)?").find(s)?.let { m ->
            fun y(t: String) = if (t.length == 2) "20$t" else t
            year = y(m.groupValues[1]) + (if (m.groupValues[2].isNotEmpty()) "–" + y(m.groupValues[2]) else "")
            if (m.groupValues[3] == "后") year += if (vi) " trở đi" else "+"
            if (m.groupValues[3] == "前") year += if (vi) " trở về trước" else " and earlier"
            s = s.removeRange(m.range)
        }
        // thế hệ: "十代", "十代半", "8代" -> đưa ra sau tên xe
        var tail = ""
        Regex("(十二|十一|十|九|八|七|六|五|四|三|二|一|\\d{1,2})代(半)?").find(s)?.let { m ->
            val n = NUM[m.groupValues[1]]?.toString() ?: m.groupValues[1]
            tail = (if (vi) "thế hệ " else "gen ") + n + (if (m.groupValues[2].isNotEmpty()) ".5" else "")
            s = s.removeRange(m.range)
        }
        // "新…" / "老…" đứng đầu = bản mới / bản cũ
        if (s.startsWith("新") && !s.startsWith("新能源") && !s.startsWith("新款")) { s = s.substring(1); tail = (tail + " " + (if (vi) "(mới)" else "(new)")).trim() }
        else if (s.startsWith("老") && !s.startsWith("老款")) { s = s.substring(1); tail = (tail + " " + (if (vi) "(cũ)" else "(old)")).trim() }
        for ((zh, name) in NAMES) if (s.contains(zh)) s = s.replace(zh, " $name ")
        for ((zh, v, e) in WORDS) if (s.contains(zh)) s = s.replace(zh, " " + (if (vi) v else e) + " ")
        s = s.replace("款", " ")
        if (tail.isNotEmpty()) s = "$s $tail"
        if (year.isNotEmpty()) s = "$s $year"
        s = s.replace(Regex("\\s+"), " ").replace(" ,", ",").replace("( ", "(").replace(" )", ")").trim()
        if (s.isEmpty()) s = src
        if (cache.size > 2000) cache.clear()
        cache[key] = s
        return s
    }
}
