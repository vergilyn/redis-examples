SET FOREIGN_KEY_CHECKS=0;

-- ----------------------------
-- Table structure for vote
-- ----------------------------
DROP TABLE IF EXISTS `vote`;
CREATE TABLE `vote` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `begin_time` datetime NOT NULL,
  `ent_time` datetime NOT NULL,
  `title` varchar(255) NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=MyISAM AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4;

-- ----------------------------
-- Records of vote
-- ----------------------------
INSERT INTO `vote` VALUES ('1', '2019-06-26 09:00:00', '2099-06-26 09:00:00', '投票活动');

-- ----------------------------
-- Table structure for vote_item
-- ----------------------------
DROP TABLE IF EXISTS `vote_item`;
CREATE TABLE `vote_item` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `count` int(11) NOT NULL,
  `name` varchar(255) NOT NULL,
  `vote_id` bigint(20) NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=MyISAM AUTO_INCREMENT=6 DEFAULT CHARSET=utf8mb4;

-- ----------------------------
-- Records of vote_item
-- ----------------------------
INSERT INTO `vote_item` VALUES ('1', '0', '投票项01', '1');
INSERT INTO `vote_item` VALUES ('2', '0', '投票项02', '1');
INSERT INTO `vote_item` VALUES ('3', '0', '投票项03', '1');
INSERT INTO `vote_item` VALUES ('4', '0', '投票项04', '1');
INSERT INTO `vote_item` VALUES ('5', '0', '投票项05', '1');

-- ----------------------------
-- Table structure for vote_log
-- ----------------------------
DROP TABLE IF EXISTS `vote_log`;
CREATE TABLE `vote_log` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `user_id` bigint(20) NOT NULL,
  `vote_id` bigint(20) NOT NULL,
  `vote_item_id` bigint(20) NOT NULL,
  `vote_time` datetime NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=MyISAM DEFAULT CHARSET=utf8mb4;

-- ----------------------------
-- Records of vote_log
-- ----------------------------
