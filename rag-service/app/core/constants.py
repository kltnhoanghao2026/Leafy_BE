import re

# ─────────────────────────────────────────────────────────────────────────────
# Disease & Pest Management Policies
# ─────────────────────────────────────────────────────────────────────────────

DISEASE_POLICIES: dict[str, dict] = {
    # ── Fungal Diseases ──────────────────────────────────────────────────────
    "leaf_rust": {
        "vietnamese": "Bệnh Rỉ Sắt",
        "scientific": "Hemileia vastatrix",
        "avoid_overhead_irrigation": True,
        "requires_sanitation": True,
        "preferred_spray_interval_days": [10, 14],
        "frac_rotation_required": True,
        "humidity_sensitive": True,
        "organic_options": ["Bordeaux mixture", "Copper-based fungicides"],
    },
    "phoma_blight": {
        "vietnamese": "Bệnh Khô Cành / Khô Quả",
        "scientific": "Phoma costarricensis (Colletotrichum complex)",
        "avoid_overhead_irrigation": True,
        "requires_sanitation": True,
        "preferred_spray_interval_days": [7, 14],
        "frac_rotation_required": True,
        "humidity_sensitive": True,
        "organic_options": ["Copper hydroxide", "Mancozeb"],
    },
    "brown_eye_spot": {
        "vietnamese": "Bệnh Thán Thư",
        "scientific": "Cercospora coffeicola",
        "avoid_overhead_irrigation": True,
        "requires_sanitation": True,
        "preferred_spray_interval_days": [14, 21],
        "frac_rotation_required": False,
        "humidity_sensitive": True,
        "organic_options": ["Bordeaux mixture", "Copper oxychloride"],
    },
    "anthracnose": {
        "vietnamese": "Bệnh Thán Thư / Đốm Quả",
        "scientific": "Colletotrichum gloeosporioides",
        "avoid_overhead_irrigation": True,
        "requires_sanitation": True,
        "preferred_spray_interval_days": [7, 14],
        "frac_rotation_required": True,
        "humidity_sensitive": True,
        "organic_options": ["Copper-based fungicides", "Neem oil"],
    },
    "root_rot": {
        "vietnamese": "Bệnh Thối Rễ",
        "scientific": "Phytophthora spp.",
        "avoid_overhead_irrigation": False,
        "requires_sanitation": True,
        "preferred_spray_interval_days": [14, 21],
        "frac_rotation_required": True,
        "humidity_sensitive": True,
        "organic_options": ["Metalaxyl (restricted)", "Fosetyl-Al"],
    },
    "pink_disease": {
        "vietnamese": "Bệnh Nấm Hồng",
        "scientific": "Erythricium salmonicolor",
        "avoid_overhead_irrigation": True,
        "requires_sanitation": True,
        "preferred_spray_interval_days": [14, 21],
        "frac_rotation_required": False,
        "humidity_sensitive": True,
        "organic_options": ["Copper fungicides", "Bordeaux mixture"],
    },
    "powdery_mildew": {
        "vietnamese": "Bệnh Phấn Trắng",
        "scientific": "Microsphaera / Erysiphe spp.",
        "avoid_overhead_irrigation": False,
        "requires_sanitation": True,
        "preferred_spray_interval_days": [7, 10],
        "frac_rotation_required": False,
        "humidity_sensitive": False,
        "organic_options": ["Sulfur", "Neem oil", "Potassium bicarbonate"],
    },
    "wilt": {
        "vietnamese": "Bệnh Héo Rũ",
        "scientific": "Fusarium oxysporum",
        "avoid_overhead_irrigation": False,
        "requires_sanitation": True,
        "preferred_spray_interval_days": [10, 14],
        "frac_rotation_required": True,
        "humidity_sensitive": False,
        "organic_options": ["Trichoderma spp.", "Soil solarization"],
    },
    "blight": {
        "vietnamese": "Bệnh Hỏa Tung / Chết Cành",
        "scientific": "Various (Botrytis, Glomerella, etc.)",
        "avoid_overhead_irrigation": True,
        "requires_sanitation": True,
        "preferred_spray_interval_days": [7, 14],
        "frac_rotation_required": True,
        "humidity_sensitive": True,
        "organic_options": ["Copper fungicides", "Bacillus-based biocontrol"],
    },
    "coffee_berry_disease": {
        "vietnamese": "Bệnh CBD (Coffee Berry Disease)",
        "scientific": "Colletotrichum kahawae complex",
        "avoid_overhead_irrigation": True,
        "requires_sanitation": True,
        "preferred_spray_interval_days": [14, 21],
        "frac_rotation_required": True,
        "humidity_sensitive": True,
        "organic_options": ["Copper-based fungicides", "Bordeaux mixture"],
    },
    # ── Viral Diseases ───────────────────────────────────────────────────────
    "mosaic_virus": {
        "vietnamese": "Bệnh Khảm Lá",
        "scientific": "Tobacco Mosaic Virus (TMV) / Cucumber Mosaic Virus (CMV)",
        "avoid_overhead_irrigation": False,
        "requires_sanitation": True,
        "preferred_spray_interval_days": None,
        "frac_rotation_required": False,
        "humidity_sensitive": False,
        "organic_options": ["No effective spray; remove infected plants", "Aphid control for CMV"],
    },
    # ── Nematode ──────────────────────────────────────────────────────────────
    "nematode": {
        "vietnamese": "Bệnh Tuyến Trùng",
        "scientific": "Meloidogyne spp.",
        "avoid_overhead_irrigation": False,
        "requires_sanitation": True,
        "preferred_spray_interval_days": None,
        "frac_rotation_required": True,
        "humidity_sensitive": False,
        "organic_options": ["Marigold trap crops", "Trichoderma", "Paecilomyces lilacinus"],
    },
    # ── Pest / Mite / Insect ─────────────────────────────────────────────────
    "leaf_miner": {
        "vietnamese": "Sâu Vẽ Bùa",
        "scientific": "Leucoptera coffeella",
        "avoid_overhead_irrigation": False,
        "requires_sanitation": True,
        "preferred_spray_interval_days": [7, 14],
        "frac_rotation_required": False,
        "humidity_sensitive": False,
        "organic_options": ["Neem oil", "Spinosad", "Bacillus thuringiensis"],
    },
    "berry_borer": {
        "vietnamese": "Mọt Đục Quả (CBB)",
        "scientific": "Hypothenemus hampei",
        "avoid_overhead_irrigation": False,
        "requires_sanitation": True,
        "preferred_spray_interval_days": [30, 45],
        "frac_rotation_required": False,
        "humidity_sensitive": False,
        "organic_options": ["Beauveria bassiana", "Metarhizium anisopliae", "Entomopathogenic nematodes"],
    },
    "white_stem_borer": {
        "vietnamese": "Mọt Đục Thân",
        "scientific": "Xylotrechus quadripes",
        "avoid_overhead_irrigation": False,
        "requires_sanitation": True,
        "preferred_spray_interval_days": [30, 60],
        "frac_rotation_required": False,
        "humidity_sensitive": False,
        "organic_options": ["Manual removal of infested branches", "Bio-pesticides"],
    },
    "mealybug": {
        "vietnamese": "Rệp Sáp",
        "scientific": "Planococcus spp.",
        "avoid_overhead_irrigation": False,
        "requires_sanitation": True,
        "preferred_spray_interval_days": [14, 21],
        "frac_rotation_required": False,
        "humidity_sensitive": False,
        "organic_options": ["Neem oil", "Insecticidal soap", "Ladybird beetle release"],
    },
    "red_spider_mite": {
        "vietnamese": "Nhện Đỏ",
        "scientific": "Tetranychus urticae / Oligonychus coffeae",
        "avoid_overhead_irrigation": False,
        "requires_sanitation": True,
        "preferred_spray_interval_days": [7, 10],
        "frac_rotation_required": False,
        "humidity_sensitive": False,
        "organic_options": ["Neem oil", "Sulfur", "Predatory mite release (Phytoseiidae)"],
    },
    # ── Nutrient Deficiency ──────────────────────────────────────────────────
    "chlorosis": {
        "vietnamese": "Bệnh Vàng Lá (Chlorosis)",
        "scientific": "Nutrient deficiency (N, Mg, Fe, K)",
        "avoid_overhead_irrigation": False,
        "requires_sanitation": False,
        "preferred_spray_interval_days": None,
        "frac_rotation_required": False,
        "humidity_sensitive": False,
        "organic_options": ["Compost", "Foliar Fe/Mg sulfate", "Balanced NPK organic"],
    },
}

# ─────────────────────────────────────────────────────────────────────────────
# Intent & Routing Patterns
# ─────────────────────────────────────────────────────────────────────────────

GREETING_RE = re.compile(
    r"^(hi|hello|hey|xin chào|chào|howdy|"
    r"good\s*(morning|afternoon|evening|day|evening)|"
    r"hola|bonjour|ciao|yo|sup|what'?s\s*up|whats?\s*up|hiya|greetings?)[\W]*$",
    re.IGNORECASE,
)

DIRECT_SIGNAL_RE = re.compile(
    r"\b(how are you|how'?re you|bạn (có )?khỏe (không|ko)|"
    r"you'?re (great|awesome|amazing|cool|nice)|"
    r"thank(s| you)|cảm ơn|thanks?|"
    r"what('?s| is) your name|who are you|bạn tên gì|bạn là ai|"
    r"tell me (a )?joke|make me (laugh|smile)|"
    r"good(bye| night)| bye |tạm biệt|see you (later|soon)|"
    r"what can you do\??|what do you know\??|can you help)\b",
    re.IGNORECASE,
)

AGRI_SIGNAL_RE = re.compile(
    r"\b("
    # ── Crop names ──────────────────────────────────────────────────────────
    r"coffee|cà phê|robusta|arabica|culi|catimor|tr4|tn1|"
    r"plant|cây|vườn|farm|nông|agri|"
    # ── Diseases (Vietnamese & scientific names) ─────────────────────────────
    r"bệnh|disease|"
    r"rỉ sắt|gỉ sắt|gỉ sét|rỉ sét|rỉ lá|gỉ lá|leaf rust|hemileia|"
    r"thán thư|brown eye spot|cercospora|"
    r"khô cành|khô quả|phytophthora|root rot|"
    r"nấm hồng|pink disease|erythricium|"
    r"tuyến trùng|nematode|meloidogyne|"
    r"héo rũ|wilt|fusarium|"
    # ── Pests ────────────────────────────────────────────────────────────────
    r"sâu|pest|mọt|borer|"
    r"mọt đục quả|mọt đục thân|berry borer|cbb|hypothenemus|"
    r"sâu đục thân|white stem borer|xylotrechus|"
    r"rệp sáp|mealybug|planococcus|"
    r"rệp vảy|scale|coccus|"
    # ── Agronomy & inputs ────────────────────────────────────────────────────
    r"soil|đất|leaf|lá|root|rễ|"
    r"fertilizer|phân bón|npk|kali|đạm|lân|"
    r"fungicide|thuốc|spray|phun|"
    r"treatment|điều trị|phòng trừ|trị bệnh|xử lý|"
    r"irrigation|tưới|tưới nước|"
    r"harvest|thu hoạch|"
    r"tây nguyên|đắk lắk|lâm đồng|gia lai|central highland|"
    # ── Planning / scheduling keywords ───────────────────────────────────────
    r"kế hoạch|lịch trình|lịch phun|phác đồ|lịch chăm sóc|lịch bón phân|"
    r"care plan|treatment plan|schedule|lập lịch|quy trình|"
    r"crop|mùa vụ"
    r")\b",
    re.IGNORECASE,
)

PLANNING_INTENT_RE = re.compile(
    r"\b(treatment plan|recovery plan|action plan|care plan|"
    r"maintenance plan|pruning plan|denoting plan|"
    r"step-by-step|what steps|schedule|spray calendar|care schedule|"
    r"irrigation schedule|fertilizer schedule|weed control schedule|"
    r"pruning schedule|denoting schedule|"
    r"give me a plan|plan for|"
    # ── Vietnamese planning keywords ───────────────────────────────────
    r"kế hoạch|lịch trình|lịch phun|phác đồ|quy trình xử lý|"
    r"lập kế hoạch|lịch chăm sóc|lịch bón phân|"
    r"chăm sóc|đốn|đốn tỉa|tỉa cành|dọn cỏ|"
    # ── Vietnamese treatment / disease-control verbs ─────────────────────
    r"điều trị|phòng trừ|trị bệnh|xử lý bệnh|kiểm soát bệnh|"
    r"phác đồ điều trị|biện pháp xử lý|cách trị|"
    # ── Vietnamese disease names that imply treatment context ──────────────
    r"rỉ sắt|gỉ sắt|gỉ sét|rỉ sét|thán thư|khô cành|nấm hồng|tuyến trùng)\b",
    re.IGNORECASE,
)

DETAIL_SIGNAL_RE = re.compile(
    r"\b(dosage|concentration|ppm|mg|kg/ha|g/ha|l/ha|treatment plan|"
    r"specific|exactly|how much|how many|rate|latest|diagnosis|"
    r"liều lượng|nồng độ|loại thuốc|phòng trừ|điều trị|bao nhiêu)\b",
    re.IGNORECASE,
)

# ─────────────────────────────────────────────────────────────────────────────
# Safety & Regulatory Constants
# ─────────────────────────────────────────────────────────────────────────────

PASS_PHRASES = (
    "no banned", "not listed as a banned", "not a banned",
    "within typical", "within range", "within the typical", "within the range",
    "is compliant", "are compliant", "is within", "are within",
    "no issues", "no violations", "not detected", "not found",
    "compliant.", "is included", "are included", "phe included",
    "ppe warning included", "has no phi", "no phi required",
    "is not listed", "not prescribed", "not recommended",
    "dosage realism:", "banned substances check:",  # LLM section headers
    "does not violate", "does not constitute", "is not a violation",
    "is below the typical", "below the typical upper", "is acceptable",
    "falls within", "this is below", "this does not",
    "impossible to assess",  # ambiguous audit note — not a real violation
    "not specified in relation",  # unit comparison difficulty — not a violation
)

EXPORT_CONTEXT_KEYWORDS = (
    "export", "xuất khẩu", "eu", "european union", "usda", "japan", "japanese",
    "premium retail", "international", "target market", "market standard", "certification",
    "rainforest alliance", "4c", "organic certification",
)

MRL_ISSUE_KEYWORDS = (
    "mrl", "residue", "maximum residue", "reg. 396/2005", "janis", "export limit",
)

BANNED_SUBSTANCES = [
    # Organochlorine pesticides (Stockholm POPs)
    "DDT", "Chlordane", "Heptachlor", "Endosulfan",
    "Lindane", "Aldrin", "Dieldrin", "Endrin",
    # Banned in Vietnam specifically (PPD List 2023)
    "Methyl parathion", "Monocrotophos", "Methamidophos",
    "Phosphamidon", "Carbofuran",   # ultra-high-tox, banned for coffee
    "Paraquat",                      # banned VN Circular 10/2020
    "Chlorpyrifos",                  # EU MRL zero-tolerance on coffee exports
    "Fipronil",                      # high bee/soil toxicity, restricted on coffee
    # Heavy metals / soil sterilants
    "Arsenic", "Mercury",
]
