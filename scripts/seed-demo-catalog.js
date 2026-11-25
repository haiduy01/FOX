#!/usr/bin/env node
/**
 * Safe, idempotent seed script for FOX Cloud Catalog DEMO data:
 *  - 2 Restaurants (FOOD): Phở Thìn FOX Quận 1, Cơm Tấm Sườn Nướng FOX
 *  - 2 Shops (SHOPPING): FOX Fresh Mart, FOX Tech & Home
 *
 * SAFETY GUARD:
 *  - Targets Project ID: gen-lang-client-0957111252
 *  - Targets Named Firestore Database ID: ai-studio-android-1a796de6-79f0-4283-8d0b-703e709b27e4
 *  - Refuses to write to live Firebase unless --allow-live is explicitly passed AND FIRESTORE_EMULATOR_HOST is absent.
 *  - Deterministic document IDs guarantee re-running NEVER creates duplicate stores or products.
 */

const path = require("path");
const { createRequire } = require("module");

const functionsRequire = createRequire(path.resolve(__dirname, "../functions/package.json"));
const { getApps, initializeApp, deleteApp } = functionsRequire("firebase-admin/app");
const { getFirestore, FieldValue } = functionsRequire("firebase-admin/firestore");

const PROJECT_ID = process.env.GCLOUD_PROJECT || "gen-lang-client-0957111252";
const FOX_DATABASE_ID =
  process.env.FOX_DATABASE_ID || "ai-studio-android-1a796de6-79f0-4283-8d0b-703e709b27e4";

const DEMO_STORES = [
  {
    merchantId: "demo_rest_pho_fox",
    ownerUid: "demo_rest_pho_fox",
    storeType: "FOOD",
    name: "Phở Thìn FOX Quận 1",
    description: "Phở bò tái lăn nước dùng ninh xương 12 giờ, phục vụ nóng hổi chuẩn vị Bắc.",
    category: "Món nước",
    address: "42 Lê Thánh Tôn, Quận 1, TP.HCM",
    phone: "0901001001",
    imageUrl: "",
    openingHours: "06:30 - 22:00",
    openNow: true,
    ratingTenths: 49,
    active: true,
    products: [
      {
        productId: "pho_tai_lan",
        name: "Phở Bò Tái Lăn Đặc Biệt",
        description: "Thịt bò thăn xào tỏi thơm lừng, nhiều hành lá, kèm quẩy nóng.",
        category: "Món nước",
        priceVnd: 65000,
        stock: 25,
        imageUrl: "",
        active: true,
      },
      {
        productId: "pho_dac_biet",
        name: "Phở Thập Cẩm Tái Nạm Gầu",
        description: "Tô đầy đủ tái, nạm, gầu, bò viên gân giòn chuẩn vị.",
        category: "Món nước",
        priceVnd: 75000,
        stock: 20,
        imageUrl: "",
        active: true,
      },
      {
        productId: "quay_gion",
        name: "Đĩa Quẩy Nóng Giòn (4 cái)",
        description: "Quẩy chiên mới giòn rụm ăn kèm nước phở.",
        category: "Ăn nhanh",
        priceVnd: 15000,
        stock: 40,
        imageUrl: "",
        active: true,
      },
      {
        productId: "tra_da_chanh",
        name: "Trà Chanh Mật Ong Size L",
        description: "Trà nhài ủ lạnh cùng mật ong hoa nhãn và chanh tươi.",
        category: "Đồ uống",
        priceVnd: 25000,
        stock: 35,
        imageUrl: "",
        active: true,
      },
    ],
  },
  {
    merchantId: "demo_rest_com_tam_fox",
    ownerUid: "demo_rest_com_tam_fox",
    storeType: "FOOD",
    name: "Cơm Tấm Sườn Nướng FOX",
    description: "Cơm tấm Sài Gòn sườn nướng than hoa, nước mắm pha kẹo đậm đà.",
    category: "Cơm",
    address: "118 Võ Văn Tần, Quận 3, TP.HCM",
    phone: "0901002002",
    imageUrl: "",
    openingHours: "09:00 - 21:30",
    openNow: true,
    ratingTenths: 48,
    active: true,
    products: [
      {
        productId: "com_suon_bi_cha",
        name: "Cơm Tấm Sườn Bì Chả Trứng",
        description: "Sườn cốt lết nướng mật ong, bì thính, chả trứng hấp và ốp la.",
        category: "Cơm",
        priceVnd: 58000,
        stock: 30,
        imageUrl: "",
        active: true,
      },
      {
        productId: "com_ga_xoi_mo",
        name: "Cơm Đùi Gà Xối Mỡ Giòn Da",
        description: "Đùi gà góc tư chiên giòn rụm, cơm đỏ gấc và canh cải ngọt.",
        category: "Cơm",
        priceVnd: 62000,
        stock: 18,
        imageUrl: "",
        active: true,
      },
      {
        productId: "canh_kho_qua",
        name: "Canh Khổ Qua Nhồi Thịt",
        description: "Khổ qua hầm mềm với nhân thịt bằm nấm mèo thanh mát.",
        category: "Món nước",
        priceVnd: 28000,
        stock: 22,
        imageUrl: "",
        active: true,
      },
      {
        productId: "tra_dao_cam_sa",
        name: "Trà Đào Cam Sả Size L",
        description: "Trà đen đào miếng giòn cùng cam vàng và sả tươi.",
        category: "Đồ uống",
        priceVnd: 39000,
        stock: 30,
        imageUrl: "",
        active: true,
      },
    ],
  },
  {
    merchantId: "demo_shop_fresh_fox",
    ownerUid: "demo_shop_fresh_fox",
    storeType: "SHOPPING",
    name: "FOX Fresh Mart",
    description: "Siêu thị thực phẩm sạch & trái cây nhập khẩu giao nhanh 30 phút.",
    category: "Thực phẩm",
    address: "215 Hai Bà Trưng, Quận 1, TP.HCM",
    phone: "0902003003",
    imageUrl: "",
    openingHours: "07:00 - 23:00",
    openNow: true,
    ratingTenths: 49,
    active: true,
    products: [
      {
        productId: "combo_trai_cay_2kg",
        name: "Combo Trái Cây Nhập Khẩu 2kg",
        description: "Gồm táo Envy New Zealand, nho mẫu đơn và cam Úc tươi mỗi ngày.",
        category: "Thực phẩm",
        priceVnd: 185000,
        stock: 15,
        imageUrl: "",
        active: true,
      },
      {
        productId: "tao_envy_1kg",
        name: "Táo Envy Size L (1kg)",
        description: "Táo Envy giòn ngọt tự nhiên, bảo quản lạnh tiêu chuẩn.",
        category: "Thực phẩm",
        priceVnd: 115000,
        stock: 24,
        imageUrl: "",
        active: true,
      },
      {
        productId: "sua_tuoi_khong_duong",
        name: "Lốc 4 Hộp Sữa Tươi Thanh Trùng",
        description: "Sữa bò tươi nguyên chất 100%, không đường, 180ml x 4.",
        category: "Thực phẩm",
        priceVnd: 46000,
        stock: 40,
        imageUrl: "",
        active: true,
      },
      {
        productId: "mat_ong_rung_500ml",
        name: "Mật Ong Hoa Cà Phê Tây Nguyên 500ml",
        description: "Mật ong nguyên chất đóng chai thủy tinh an toàn thực phẩm.",
        category: "Thực phẩm",
        priceVnd: 145000,
        stock: 12,
        imageUrl: "",
        active: true,
      },
    ],
  },
  {
    merchantId: "demo_shop_tech_fox",
    ownerUid: "demo_shop_tech_fox",
    storeType: "SHOPPING",
    name: "FOX Tech & Home",
    description: "Phụ kiện công nghệ, bình giữ nhiệt và đồ gia dụng thông minh chính hãng.",
    category: "Công nghệ",
    address: "68 Nguyễn Trãi, Quận 5, TP.HCM",
    phone: "0902004004",
    imageUrl: "",
    openingHours: "08:30 - 21:30",
    openNow: true,
    ratingTenths: 47,
    active: true,
    products: [
      {
        productId: "binh_giu_nhiet_750ml",
        name: "Bình Giữ Nhiệt Inox 304 750ml",
        description: "Giữ nóng 12 giờ, giữ lạnh 24 giờ, lớp sơn tĩnh điện chống trầy.",
        category: "Gia dụng",
        priceVnd: 229000,
        stock: 16,
        imageUrl: "",
        active: true,
      },
      {
        productId: "tai_nghe_anc_fox",
        name: "Tai Nghe Bluetooth Chống Ồn ANC",
        description: "Bluetooth 5.3, chống ồn chủ động, pin nghe liên tục 28 giờ.",
        category: "Công nghệ",
        priceVnd: 350000,
        stock: 10,
        imageUrl: "",
        active: true,
      },
      {
        productId: "sac_nhanh_gan_65w",
        name: "Củ Sạc Nhanh GaN 65W 3 Cổng",
        description: "Hỗ trợ PD 65W cho laptop & điện thoại, nhỏ gọn an toàn.",
        category: "Công nghệ",
        priceVnd: 295000,
        stock: 14,
        imageUrl: "",
        active: true,
      },
      {
        productId: "den_ban_hoc_led",
        name: "Đèn Bàn LED Chống Cận 3 Chế Độ",
        description: "Ánh sáng chuẩn CRI>90, cảm ứng chạm điều chỉnh độ sáng.",
        category: "Gia dụng",
        priceVnd: 190000,
        stock: 20,
        imageUrl: "",
        active: true,
      },
    ],
  },
];

/**
 * Idempotently seeds 2 demo restaurants and 2 demo shops with their products.
 * Safe to run repeatedly: uses deterministic doc IDs and preserves createdAt.
 */
async function seedDemoCatalog(db) {
  const now = FieldValue.serverTimestamp();
  let storesUpserted = 0;
  let productsUpserted = 0;

  for (const store of DEMO_STORES) {
    const storeRef = db.collection("merchants").doc(store.merchantId);
    const existingStore = await storeRef.get();
    const storePayload = {
      merchantId: store.merchantId,
      ownerUid: store.ownerUid,
      storeType: store.storeType,
      name: store.name,
      description: store.description,
      category: store.category,
      address: store.address,
      phone: store.phone,
      imageUrl: store.imageUrl,
      openingHours: store.openingHours,
      openNow: store.openNow,
      ratingTenths: store.ratingTenths,
      active: store.active,
      createdAt: existingStore.exists ? existingStore.data().createdAt || now : now,
      updatedAt: now,
    };
    await storeRef.set(storePayload, { merge: true });
    storesUpserted += 1;

    for (const prod of store.products) {
      const prodRef = storeRef.collection("products").doc(prod.productId);
      const existingProd = await prodRef.get();
      const prodPayload = {
        merchantId: store.merchantId,
        storeName: store.name,
        serviceType: store.storeType,
        name: prod.name,
        description: prod.description,
        imageUrl: prod.imageUrl,
        category: prod.category,
        priceVnd: prod.priceVnd,
        stock: prod.stock,
        active: prod.active,
        createdAt: existingProd.exists ? existingProd.data().createdAt || now : now,
        updatedAt: now,
      };
      await prodRef.set(prodPayload, { merge: true });
      productsUpserted += 1;
    }
  }

  return { storesUpserted, productsUpserted, storeIds: DEMO_STORES.map((s) => s.merchantId) };
}

if (require.main === module) {
  const allowLive = process.argv.includes("--allow-live");
  const emulatorHost = process.env.FIRESTORE_EMULATOR_HOST;
  if (!emulatorHost && !allowLive) {
    console.error(
      "[SAFETY BLOCK] FIRESTORE_EMULATOR_HOST is not set. To prevent accidental writes to live Firebase, pass --allow-live explicitly when running against live project " +
        PROJECT_ID,
    );
    process.exit(1);
  }

  (async () => {
    const app =
      getApps().length > 0
        ? getApps()[0]
        : initializeApp({ projectId: PROJECT_ID }, "fox-seed-catalog");
    const db = getFirestore(app, FOX_DATABASE_ID);
    const result = await seedDemoCatalog(db);
    console.log(
      `[FOX SEED OK] target=${emulatorHost || "LIVE:" + PROJECT_ID} db=${FOX_DATABASE_ID} stores=${result.storesUpserted} products=${result.productsUpserted}`,
    );
    await deleteApp(app);
  })().catch((err) => {
    console.error("[FOX SEED ERROR]", err);
    process.exit(1);
  });
}

module.exports = {
  DEMO_STORES,
  PROJECT_ID,
  FOX_DATABASE_ID,
  seedDemoCatalog,
};
