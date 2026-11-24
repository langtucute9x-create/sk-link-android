package vn.sklight.app

import java.util.Calendar

/**
 * Danh sách hãng xe / dòng xe đang bán hoặc đang chạy phổ biến ở Việt Nam (soạn 24/11/2026).
 * Mỗi dòng: "Hãng|Dòng:năm bắt đầu có ở Việt Nam,Dòng:năm,...". Năm chỉ để giới hạn danh sách "Đời xe",
 * cố ý để sớm hơn một chút cho an toàn. Xe không có trong danh sách thì thợ chọn "Dùng tên đã gõ".
 * Thêm xe mới: chỉ cần thêm một mục vào đúng dòng của hãng (hoặc dòng mới), không sửa chỗ khác.
 */
object CarData {
    private const val RAW = """
VinFast|VF 2:2026,VF 3:2024,VF 5:2022,VF 6:2023,VF 7:2023,VF 8:2022,VF 9:2023,VF e34:2021,VF MPV 7:2025,Minio Green:2024,Herio Green:2024,Nerio Green:2024,Limo Green:2024,EC Van:2024,Fadil:2019,Lux A2.0:2019,Lux SA2.0:2019,President:2020
Toyota|Vios:2003,Yaris:2006,Yaris Cross:2023,Wigo:2018,Raize:2022,Avanza Premio:2022,Veloz Cross:2022,Corolla Altis:2001,Corolla Cross:2020,Camry:2002,Crown:2023,Innova:2006,Innova Cross:2023,Rush:2018,Fortuner:2009,Land Cruiser:2008,Land Cruiser Prado:2009,Hilux:2005,Hiace:2005,Alphard:2015,Vellfire:2018,Sienna:2010,Highlander:2008,bZ4X:2023,Zace:2003
Hyundai|Grand i10:2014,i10:2008,Accent:2011,Elantra:2001,Sonata:2008,Creta:2022,Stargazer:2022,Stargazer X:2023,Custin:2023,Kona:2018,Tucson:2005,Santa Fe:2006,Palisade:2019,Ioniq 5:2022,Ioniq 6:2024,Solati:2017
Kia|Morning:2008,Soluto:2019,Rio:2012,K3:2013,Cerato:2009,K5:2020,Optima:2012,Seltos:2020,Sonet:2021,Sportage:2008,Sorento:2010,Carens:2022,Rondo:2008,Carnival:2015,Sedona:2014,EV6:2022,EV9:2024
Mazda|Mazda2:2015,Mazda3:2004,Mazda6:2003,CX-3:2016,CX-30:2021,CX-5:2013,CX-8:2019,CX-9:2013,CX-60:2023,MX-5:2016,BT-50:2012
Honda|Brio:2019,Jazz:2008,City:2003,Civic:2006,Accord:2003,HR-V:2022,BR-V:2023,CR-V:2006,Odyssey:2016,Pilot:2018
Ford|Fiesta:2010,Focus:2005,Mondeo:2004,EcoSport:2014,Escape:2004,Territory:2022,Explorer:2011,Everest:2005,Ranger:2005,Ranger Raptor:2018,Transit:2005,Mustang:2015,Mustang Mach-E:2025,F-150:2021
Mitsubishi|Mirage:2013,Attrage:2014,Xpander:2018,Xpander Cross:2020,Outlander Sport:2013,Outlander:2008,Destinator:2025,Triton:2005,Pajero Sport:2009,Pajero:2000,Zinger:2008,Grandis:2005
Suzuki|Celerio:2018,Swift:2007,Ciaz:2016,Ertiga:2013,XL7:2020,Grand Vitara:2005,Jimny:2019,Carry Pro:2015,Carry Truck:2005
Nissan|Almera:2021,Sunny:2013,Teana:2008,Kicks:2022,X-Trail:2005,Terra:2018,Navara:2012,Grand Livina:2010
Subaru|Impreza:2010,Crosstrek:2013,Forester:2008,Outback:2010
Isuzu|D-Max:2007,mu-X:2014
Chevrolet|Spark:2008,Aveo:2011,Lacetti:2004,Cruze:2009,Captiva:2007,Orlando:2011,Colorado:2012,Trailblazer:2018
Mercedes-Benz|A-Class:2013,CLA:2014,C-Class:2001,E-Class:2001,S-Class:2001,GLA:2014,GLB:2020,GLC:2015,GLE:2015,GLS:2016,G-Class:2010,V-Class:2015,Maybach S-Class:2016,Maybach GLS:2020,EQB:2023,EQE:2023,EQS:2022
BMW|2 Series Gran Coupe:2020,3 Series:2005,5 Series:2003,7 Series:2003,X1:2010,X2:2018,X3:2005,X5:2005,X6:2010,X7:2019,Z4:2019,i4:2022,i5:2023,i7:2023,iX:2022,iX3:2022
Audi|A3:2013,A4:2008,A5:2010,A6:2005,A7:2012,A8:2010,Q2:2017,Q3:2013,Q5:2010,Q7:2008,Q8:2019,e-tron:2020
Lexus|IS:2006,ES:2006,LS:2006,UX:2019,NX:2015,RX:2006,GX:2010,LX:2010,LM:2024,RZ:2023
Volvo|S60:2018,S90:2017,XC40:2018,XC60:2017,XC90:2016
Peugeot|2008:2021,3008:2017,5008:2017,408:2023,508:2015,Traveller:2019
Porsche|911:2007,Panamera:2010,Macan:2014,Cayenne:2005,Taycan:2021
Land Rover|Range Rover Evoque:2012,Discovery Sport:2015,Discovery:2017,Range Rover Velar:2018,Range Rover Sport:2008,Range Rover:2005,Defender:2020
Mini|Cooper:2014,Countryman:2017
Jeep|Wrangler:2018,Grand Cherokee:2018
Rolls-Royce|Ghost:2010,Cullinan:2018,Phantom:2005,Spectre:2024
Bentley|Continental GT:2005,Flying Spur:2006,Bentayga:2017
Maserati|Ghibli:2014,Levante:2017,Grecale:2023
Lamborghini|Huracán:2015,Urus:2018
MG|MG5:2022,ZS:2021,HS:2022,MG4 EV:2023
BYD|Atto 2:2025,Atto 3:2024,Dolphin:2024,Seal:2024,Seal 5 DM-i:2025,Sealion 6 DM-i:2025,Han:2024,Tang:2024
Geely|EX2:2025,EX5:2025,Coolray:2024,Okavango:2025
Lynk & Co|02:2025,03:2025,06:2026,900:2025
Omoda|C5:2024,C5 SHS-H:2026,C7:2025
Jaecoo|J5:2025,J7:2025,J7 SHS:2025
Haval|H6:2023,Jolion:2023,Tank 300:2024
GAC|GS3 Emzoom:2026,GS8:2024
Wuling|Mini EV:2023,Bingo:2024,Grango:2026
Zeekr|7X:2025
"""

    private val map: LinkedHashMap<String, LinkedHashMap<String, Int>> by lazy {
        val m = LinkedHashMap<String, LinkedHashMap<String, Int>>()
        for (ln in RAW.lineSequence()) {
            val t = ln.trim(); if (t.isEmpty()) continue
            val i = t.indexOf('|'); if (i <= 0) continue
            val ms = LinkedHashMap<String, Int>()
            for (p in t.substring(i + 1).split(',')) {
                val c = p.lastIndexOf(':'); if (c <= 0) continue
                ms[p.substring(0, c).trim()] = p.substring(c + 1).trim().toIntOrNull() ?: 2000
            }
            m[t.substring(0, i).trim()] = ms
        }
        m
    }

    fun brands(): List<String> = map.keys.toList()
    fun series(brand: String): List<String> = map[brand]?.keys?.toList() ?: emptyList()

    /** Các năm để chọn, mới nhất trước. Dòng chưa có trong danh sách: từ 1995. */
    fun years(brand: String, series: String): List<String> {
        val now = Calendar.getInstance().get(Calendar.YEAR) + 1
        val from = map[brand]?.get(series) ?: 1995
        return (now downTo from.coerceAtMost(now)).map { it.toString() }
    }
}
