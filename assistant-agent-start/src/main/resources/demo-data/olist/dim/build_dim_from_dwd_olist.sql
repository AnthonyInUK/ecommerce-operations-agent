TRUNCATE TABLE dim_olist_regions;
TRUNCATE TABLE dim_olist_categories;
TRUNCATE TABLE dim_olist_products;

INSERT INTO dim_olist_regions (
    state_code,
    region_name_seed,
    macro_region_group,
    source_tag
)
SELECT DISTINCT
    customer_state AS state_code,
    CASE
        WHEN customer_state IN ('SP', 'RJ', 'MG', 'ES') THEN '华东'
        WHEN customer_state IN ('PR', 'SC', 'RS') THEN '华南'
        ELSE '其他区域'
    END AS region_name_seed,
    CASE
        WHEN customer_state IN ('SP', 'RJ', 'MG', 'ES') THEN 'Southeast'
        WHEN customer_state IN ('PR', 'SC', 'RS') THEN 'South'
        WHEN customer_state IN ('BA', 'PE', 'CE', 'PB', 'RN', 'AL', 'SE', 'PI', 'MA') THEN 'Northeast'
        WHEN customer_state IN ('DF', 'GO', 'MT', 'MS') THEN 'Center-West'
        WHEN customer_state IN ('AM', 'PA', 'RO', 'RR', 'AP', 'AC', 'TO') THEN 'North'
        ELSE 'Unknown'
    END AS macro_region_group,
    'olist_dwd'
FROM dwd_olist_orders
WHERE customer_state IS NOT NULL;

INSERT INTO dim_olist_categories (
    product_category_name_raw,
    category_l1_seed,
    category_name_cn_seed,
    source_tag
)
SELECT DISTINCT
    product_category_name AS product_category_name_raw,
    CASE
        WHEN product_category_name IN ('beleza_saude', 'perfumaria', 'fraldas_higiene') THEN '美妆'
        WHEN product_category_name IN (
            'fashion_bolsas_e_acessorios',
            'fashion_roupa_feminina',
            'fashion_roupa_masculina',
            'fashion_calcados',
            'fashion_underwear_e_moda_praia',
            'fashion_esporte',
            'malas_acessorios'
        ) THEN '女装'
        WHEN product_category_name IN (
            'eletrodomesticos',
            'eletrodomesticos_2',
            'eletroportateis',
            'eletroportateis_casa_forno_e_cafe',
            'climatizacao'
        ) THEN '家电'
        WHEN product_category_name IN (
            'eletronicos',
            'informatica_acessorios',
            'pcs',
            'pc_gamer',
            'tablets_impressao_imagem',
            'telefonia',
            'telefonia_fixa',
            'audio',
            'cine_foto',
            'consoles_games',
            'dvds_blu_ray'
        ) THEN '数码'
        WHEN product_category_name IN (
            'moveis_decoracao',
            'moveis_escritorio',
            'moveis_sala',
            'moveis_quarto',
            'moveis_colchao_e_estofado',
            'moveis_cozinha_area_de_servico_jantar_e_jardim',
            'cama_mesa_banho',
            'utilidades_domesticas',
            'casa_conforto',
            'casa_conforto_2',
            'casa_construcao',
            'construcao_ferramentas_construcao',
            'construcao_ferramentas_ferramentas',
            'construcao_ferramentas_iluminacao',
            'construcao_ferramentas_jardim',
            'construcao_ferramentas_seguranca',
            'ferramentas_jardim',
            'iluminacao_jardim'
        ) THEN '家居'
        WHEN product_category_name IN ('bebes', 'brinquedos') THEN '母婴玩具'
        WHEN product_category_name IN ('esporte_lazer') THEN '运动户外'
        WHEN product_category_name IN ('alimentos', 'alimentos_bebidas', 'bebidas') THEN '食品'
        WHEN product_category_name IN ('livros_interesse_geral', 'livros_tecnicos', 'livros_importados', 'papelaria', 'artes') THEN '图书文创'
        WHEN product_category_name IN ('cool_stuff') THEN '生活杂货'
        WHEN product_category_name IN ('automotivo') THEN '汽配'
        WHEN product_category_name IN ('pet_shop') THEN '宠物'
        WHEN product_category_name IN ('relogios_presentes', 'joias') THEN '珠宝配饰'
        ELSE '其他品类'
    END AS category_l1_seed,
    CASE
        WHEN product_category_name IN ('beleza_saude', 'perfumaria', 'fraldas_higiene') THEN '美妆'
        WHEN product_category_name IN (
            'fashion_bolsas_e_acessorios',
            'fashion_roupa_feminina',
            'fashion_roupa_masculina',
            'fashion_calcados',
            'fashion_underwear_e_moda_praia',
            'fashion_esporte',
            'malas_acessorios'
        ) THEN '女装'
        WHEN product_category_name IN (
            'eletrodomesticos',
            'eletrodomesticos_2',
            'eletroportateis',
            'eletroportateis_casa_forno_e_cafe',
            'climatizacao'
        ) THEN '家电'
        WHEN product_category_name IN (
            'eletronicos',
            'informatica_acessorios',
            'pcs',
            'pc_gamer',
            'tablets_impressao_imagem',
            'telefonia',
            'telefonia_fixa',
            'audio',
            'cine_foto',
            'consoles_games',
            'dvds_blu_ray'
        ) THEN '数码'
        WHEN product_category_name IN (
            'moveis_decoracao',
            'moveis_escritorio',
            'moveis_sala',
            'moveis_quarto',
            'moveis_colchao_e_estofado',
            'moveis_cozinha_area_de_servico_jantar_e_jardim',
            'cama_mesa_banho',
            'utilidades_domesticas',
            'casa_conforto',
            'casa_conforto_2',
            'casa_construcao',
            'construcao_ferramentas_construcao',
            'construcao_ferramentas_ferramentas',
            'construcao_ferramentas_iluminacao',
            'construcao_ferramentas_jardim',
            'construcao_ferramentas_seguranca',
            'ferramentas_jardim',
            'iluminacao_jardim'
        ) THEN '家居'
        WHEN product_category_name IN ('bebes', 'brinquedos') THEN '母婴玩具'
        WHEN product_category_name IN ('esporte_lazer') THEN '运动户外'
        WHEN product_category_name IN ('alimentos', 'alimentos_bebidas', 'bebidas') THEN '食品'
        WHEN product_category_name IN ('livros_interesse_geral', 'livros_tecnicos', 'livros_importados', 'papelaria', 'artes') THEN '图书文创'
        WHEN product_category_name IN ('cool_stuff') THEN '生活杂货'
        WHEN product_category_name IN ('automotivo') THEN '汽配'
        WHEN product_category_name IN ('pet_shop') THEN '宠物'
        WHEN product_category_name IN ('relogios_presentes', 'joias') THEN '珠宝配饰'
        ELSE product_category_name
    END AS category_name_cn_seed,
    'olist_dwd'
FROM dwd_olist_order_items
WHERE product_category_name IS NOT NULL;

INSERT INTO dim_olist_products (
    product_id,
    product_category_name_raw,
    category_l1_seed,
    weight_band_seed,
    source_tag
)
SELECT DISTINCT
    p.product_id,
    p.product_category_name,
    COALESCE(c.category_l1_seed, '其他品类') AS category_l1_seed,
    CASE
        WHEN rp.product_weight_g IS NULL THEN 'unknown'
        WHEN rp.product_weight_g < 500 THEN 'light'
        WHEN rp.product_weight_g < 2000 THEN 'medium'
        ELSE 'heavy'
    END AS weight_band_seed,
    'olist_dwd'
FROM dwd_olist_order_items p
LEFT JOIN dim_olist_categories c
       ON p.product_category_name = c.product_category_name_raw
LEFT JOIN raw_olist_products rp
       ON p.product_id = rp.product_id
WHERE p.product_id IS NOT NULL;
