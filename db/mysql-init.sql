CREATE DATABASE IF NOT EXISTS wu_jia_you_chong
    DEFAULT CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE wu_jia_you_chong;

CREATE TABLE IF NOT EXISTS user_account (
    id BIGINT NOT NULL AUTO_INCREMENT,
    username VARCHAR(64) NOT NULL,
    password VARCHAR(255) NOT NULL,
    role VARCHAR(32) NOT NULL,
    nickname VARCHAR(64),
    avatar_url VARCHAR(512),
    avatar_data LONGBLOB,
    avatar_content_type VARCHAR(64),
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_account_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS pet_friendly_place (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(120) NOT NULL,
    type VARCHAR(20) NOT NULL,
    address VARCHAR(240),
    latitude DOUBLE,
    longitude DOUBLE,
    description VARCHAR(500),
    uploaded_by_id BIGINT NOT NULL,
    PRIMARY KEY (id),
    KEY idx_place_uploaded_by_id (uploaded_by_id),
    KEY idx_place_type (type),
    CONSTRAINT fk_place_uploaded_by
        FOREIGN KEY (uploaded_by_id) REFERENCES user_account (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO user_account (username, password, role, nickname)
SELECT 'admin', '$2a$10$4Cw7f6SxAIAZpLKw2cdHAO0DChfyz1mPglqJEmo7Tozk05LXMQnaS', 'ROLE_ADMIN', 'admin'
WHERE NOT EXISTS (
    SELECT 1 FROM user_account WHERE username = 'admin'
);

INSERT INTO pet_friendly_place (name, type, address, latitude, longitude, description, uploaded_by_id)
SELECT seed.name, seed.type, seed.address, seed.latitude, seed.longitude, seed.description, ua.id
FROM user_account ua
JOIN (
    SELECT '世纪公园宠物友好环线' name, 'PARK' type, '上海市浦东新区锦绣路1001号' address, 31.2161 latitude, 121.5516 longitude, '步道宽，适合牵引散步，周末人多。' description
    UNION ALL SELECT '滨江森林公园外围步道', 'PARK', '上海市浦东新区高桥镇凌桥高沙滩3号', 31.3905, 121.5669, '空气好，适合长距离散步。'
    UNION ALL SELECT '徐汇滨江宠物散步带', 'PARK', '上海市徐汇区龙腾大道', 31.1845, 121.4619, '江边视野开阔，傍晚体验好。'
    UNION ALL SELECT '大宁灵石公园友好区', 'PARK', '上海市静安区广中西路288号', 31.2825, 121.4504, '树荫多，适合夏天短途散步。'
    UNION ALL SELECT '静安雕塑公园周边步道', 'PARK', '上海市静安区石门二路128号', 31.2366, 121.4652, '城市中心，适合小型犬短时活动。'
    UNION ALL SELECT '长风公园外圈步道', 'PARK', '上海市普陀区大渡河路189号', 31.2248, 121.3995, '湖边路线清晰，建议避开高峰。'
    UNION ALL SELECT '复兴公园周边散步区', 'PARK', '上海市黄浦区皋兰路2号', 31.2157, 121.4707, '市中心老公园，适合牵引散步。'
    UNION ALL SELECT '前滩休闲公园', 'PARK', '上海市浦东新区前滩大道', 31.1514, 121.4888, '草地和步道兼具，适合拍照。'
    UNION ALL SELECT '杨浦滨江步道', 'PARK', '上海市杨浦区秦皇岛路', 31.2582, 121.5352, '江边路线长，适合精力旺盛的狗狗。'
    UNION ALL SELECT '上海植物园外围友好步道', 'PARK', '上海市徐汇区龙吴路1111号', 31.1488, 121.4488, '绿化密集，适合慢走。'
    UNION ALL SELECT '顾村公园外围散步点', 'PARK', '上海市宝山区沪太路4788号', 31.3447, 121.3736, '空间大，建议提前确认入园规则。'
    UNION ALL SELECT '虹桥天地宠物友好街区', 'MALL', '上海市闵行区申长路688号', 31.1943, 121.3171, '商场外街区较友好，适合短暂停留。'
    UNION ALL SELECT '瑞虹天地宠物友好商业区', 'MALL', '上海市虹口区瑞虹路188号', 31.2705, 121.5043, '餐饮选择多，注意店铺单独规则。'
    UNION ALL SELECT '陆家嘴滨江亲宠步道', 'SCENIC', '上海市浦东新区滨江大道', 31.2399, 121.5079, '夜景好，适合晚间散步。'
    UNION ALL SELECT '外滩观景步道', 'SCENIC', '上海市黄浦区中山东一路', 31.2396, 121.4909, '经典江景路线，建议错峰牵引通行。'
    UNION ALL SELECT '武康路历史风貌街区', 'SCENIC', '上海市徐汇区武康路', 31.2074, 121.4427, '适合拍照慢走，路窄需牵引。'
    UNION ALL SELECT '思南公馆街区', 'SCENIC', '上海市黄浦区复兴中路523号', 31.2149, 121.4683, '街区步行体验好，注意店铺单独规则。'
    UNION ALL SELECT '上海K11宠物友好商场', 'MALL', '上海市黄浦区淮海中路300号', 31.2232, 121.4751, '商场动线清晰，进店前确认宠物规则。'
    UNION ALL SELECT '前滩太古里宠物友好区', 'MALL', '上海市浦东新区东育路500弄', 31.1516, 121.4885, '开放式街区商业，适合牵引散步。'
    UNION ALL SELECT '静安嘉里中心宠物友好外街', 'MALL', '上海市静安区南京西路1515号', 31.2242, 121.4466, '外摆区域友好，餐饮需单独确认。'
    UNION ALL SELECT '南翔印象城MEGA宠物友好区', 'MALL', '上海市嘉定区陈翔公路2299号', 31.2991, 121.3212, '空间大，适合周末休闲。'
    UNION ALL SELECT '上海宠物友好精品酒店A', 'HOTEL', '上海市黄浦区南京东路附近', 31.2358, 121.4836, '示例酒店，建议预订前电话确认宠物政策。'
    UNION ALL SELECT '浦东机场周边亲宠酒店', 'HOTEL', '上海市浦东新区机场镇', 31.1499, 121.7996, '适合出行中转，需确认体型限制。'
    UNION ALL SELECT '虹桥枢纽宠物友好酒店', 'HOTEL', '上海市闵行区虹桥商务区', 31.1937, 121.3205, '交通方便，适合短住。'
    UNION ALL SELECT '外滩亲宠江景酒店', 'HOTEL', '上海市黄浦区中山东一路', 31.2394, 121.4907, '江景区域，入住前确认清洁费。'
    UNION ALL SELECT '静安寺宠物友好酒店', 'HOTEL', '上海市静安区南京西路', 31.2231, 121.4457, '商圈便利，适合小型犬。'
    UNION ALL SELECT '徐家汇亲宠公寓酒店', 'HOTEL', '上海市徐汇区漕溪北路', 31.1957, 121.4376, '公寓式房型，适合多日停留。'
    UNION ALL SELECT '迪士尼周边宠物友好民宿', 'HOTEL', '上海市浦东新区川沙新镇', 31.1419, 121.6677, '适合亲子和宠物出行组合。'
    UNION ALL SELECT '松江佘山亲宠度假酒店', 'HOTEL', '上海市松江区佘山镇', 31.0964, 121.1897, '郊区环境安静，适合周末。'
    UNION ALL SELECT '青浦朱家角亲宠客栈', 'HOTEL', '上海市青浦区朱家角镇', 31.1082, 121.0569, '古镇周边，建议错峰出行。'
    UNION ALL SELECT '临港滴水湖宠物友好酒店', 'HOTEL', '上海市浦东新区滴水湖', 30.9075, 121.9296, '湖边度假，适合自驾。'
    UNION ALL SELECT '世博园遛狗草坪', 'LAWN', '上海市浦东新区世博大道', 31.1859, 121.4931, '草坪开阔，注意文明清理。'
    UNION ALL SELECT '前滩运动公园草坪', 'LAWN', '上海市浦东新区前滩休闲公园内', 31.1496, 121.4898, '适合飞盘和基础训练。'
    UNION ALL SELECT '新江湾城生态草坪', 'LAWN', '上海市杨浦区淞沪路', 31.3353, 121.5135, '周边安静，适合晨间活动。'
    UNION ALL SELECT '闵行体育公园草坪', 'LAWN', '上海市闵行区新镇路456号', 31.1552, 121.3674, '空间充足，适合中大型犬散步。'
    UNION ALL SELECT '古美社区遛狗草坪', 'LAWN', '上海市闵行区古美路', 31.1478, 121.3992, '社区友好点，适合短时放松。'
    UNION ALL SELECT '中山公园外圈草坪', 'LAWN', '上海市长宁区长宁路780号', 31.2208, 121.4208, '交通方便，适合工作日晚间。'
    UNION ALL SELECT '金桥碧云草坪区', 'LAWN', '上海市浦东新区碧云路', 31.2423, 121.5846, '社区环境好，宠物家庭较多。'
    UNION ALL SELECT '张江人才公园草坪', 'LAWN', '上海市浦东新区环科路', 31.2032, 121.5999, '适合午后散步和轻训练。'
    UNION ALL SELECT '嘉定远香湖草坪', 'LAWN', '上海市嘉定区白银路', 31.3548, 121.2546, '湖边草坪，适合周末自驾。'
    UNION ALL SELECT '奉贤泡泡公园草坪', 'LAWN', '上海市奉贤区湖畔路', 30.9171, 121.4732, '空间开阔，人流相对分散。'
    UNION ALL SELECT '宝山滨江草坪', 'LAWN', '上海市宝山区牡丹江路', 31.4061, 121.4981, '江边风大，适合春秋季。'
    UNION ALL SELECT '浦东金海湿地草坪', 'LAWN', '上海市浦东新区金海路', 31.2676, 121.6368, '自然感强，建议做好防虫。'
    UNION ALL SELECT '桃浦中央绿地草坪', 'LAWN', '上海市普陀区真南路', 31.2868, 121.3798, '新绿地环境，适合探索。'
    UNION ALL SELECT '北外滩滨江友好点', 'SCENIC', '上海市虹口区东大名路', 31.2506, 121.5058, '江景路线，适合小型犬牵引散步。'
    UNION ALL SELECT '黄兴公园周边草坪', 'LAWN', '上海市杨浦区营口路699号', 31.2898, 121.5347, '社区氛围浓，适合日常遛狗。'
    UNION ALL SELECT '虹梅路亲宠酒店', 'HOTEL', '上海市闵行区虹梅路', 31.1712, 121.3976, '周边生活便利，入住前确认押金规则。'
) seed ON 1 = 1
WHERE ua.username = 'admin'
  AND NOT EXISTS (
      SELECT 1 FROM pet_friendly_place p WHERE p.name = seed.name
  );
