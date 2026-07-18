-- MySQL dump 10.13  Distrib 8.0.31, for macos12 (arm64)
--
-- Host: localhost    Database: private_cloud_disk
-- ------------------------------------------------------
-- Server version	8.0.31

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!50503 SET NAMES utf8mb4 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

--
-- Table structure for table `pcd_client_identities`
--

DROP TABLE IF EXISTS `pcd_client_identities`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `pcd_client_identities` (
  `client_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'å®¢æˆ·ç«¯å”¯ä¸€æ ‡è¯†ï¼ˆUUID v4ï¼‰',
  `device_id` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'è®¾å¤‡ç¡¬ä»¶æŒ‡çº¹ï¼ˆSHA-256 å“ˆå¸Œï¼‰',
  `platform` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'macOS' COMMENT 'å¹³å°æ ‡è¯†',
  `app_id` varchar(256) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'åº”ç”¨ Bundle ID',
  `public_key` text COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'ECDSA P-256 å…¬é’¥ï¼ˆBase64 DERï¼‰',
  `key_algorithm` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'ECDSA-P256' COMMENT 'å¯†é’¥ç®—æ³•',
  `token_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'å¯†é’¥å­˜å‚¨ä½ç½®ï¼ˆSecureEnclave/Keychainï¼‰',
  `integrity_level` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'medium' COMMENT 'å®Œæ•´æ€§ç­‰çº§',
  `os_version` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT 'æ“ä½œç³»ç»Ÿç‰ˆæœ¬',
  `hostname` varchar(256) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT 'è®¾å¤‡ä¸»æœºå',
  `status` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'active' COMMENT 'çŠ¶æ€: active/revoked/pending',
  `registered_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'æ³¨å†Œæ—¶é—´',
  `last_verified_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'æœ€åéªŒè¯æ—¶é—´',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'åˆ›å»ºæ—¶é—´',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'æ›´æ–°æ—¶é—´',
  PRIMARY KEY (`client_id`),
  KEY `idx_device_id` (`device_id`),
  KEY `idx_app_id` (`app_id`),
  KEY `idx_status` (`status`),
  KEY `idx_registered_at` (`registered_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='å®¢æˆ·ç«¯è®¾å¤‡èº«ä»½æ³¨å†Œè¡¨';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `pcd_client_identities`
--

LOCK TABLES `pcd_client_identities` WRITE;
/*!40000 ALTER TABLE `pcd_client_identities` DISABLE KEYS */;
/*!40000 ALTER TABLE `pcd_client_identities` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `pcd_directory_closure_table`
--

DROP TABLE IF EXISTS `pcd_directory_closure_table`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `pcd_directory_closure_table` (
  `user_id` binary(16) NOT NULL COMMENT 'æ‰€å±ç”¨æˆ·ID',
  `ancestor_id` binary(16) NOT NULL COMMENT 'ç¥–å…ˆèŠ‚ç‚¹ID',
  `descendant_id` binary(16) NOT NULL COMMENT 'åä»£èŠ‚ç‚¹ID',
  `depth` int NOT NULL COMMENT 'ç¥–å…ˆä¸åä»£èŠ‚ç‚¹æ·±åº¦',
  PRIMARY KEY (`ancestor_id`,`descendant_id`),
  UNIQUE KEY `uk_descendant` (`user_id`,`descendant_id`,`ancestor_id`),
  KEY `descendant_id` (`descendant_id`),
  KEY `idx_depth` (`depth`),
  CONSTRAINT `pcd_directory_closure_table_ibfk_1` FOREIGN KEY (`user_id`) REFERENCES `pcd_user_info_table` (`user_id`) ON DELETE CASCADE,
  CONSTRAINT `pcd_directory_closure_table_ibfk_2` FOREIGN KEY (`ancestor_id`) REFERENCES `pcd_directory_tree_table` (`node_id`) ON DELETE CASCADE,
  CONSTRAINT `pcd_directory_closure_table_ibfk_3` FOREIGN KEY (`descendant_id`) REFERENCES `pcd_directory_tree_table` (`node_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='ç›®å½•æ ‘é—­åŒ…è¡¨';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `pcd_directory_closure_table`
--

LOCK TABLES `pcd_directory_closure_table` WRITE;
/*!40000 ALTER TABLE `pcd_directory_closure_table` DISABLE KEYS */;
INSERT INTO `pcd_directory_closure_table` VALUES (_binary 'UUUUUUUUUUUUUUUU',_binary 'n$ûOœB&‰©°c',_binary 'n$ûOœB&‰©°c',0),(_binary 'UUUUUUUUUUUUUUUU',_binary 'n$ûOœB&‰©°c',_binary 'M7™Œ\ßZF×½+\Èé®»“	',2),(_binary 'UUUUUUUUUUUUUUUU',_binary 'n$ûOœB&‰©°c',_binary 'Vyƒ¢IK·§X‚‰V^',1),(_binary 'UUUUUUUUUUUUUUUU',_binary 'n$ûOœB&‰©°c',_binary '\ËYú˜~@â‘®]%\Ş',2),(_binary 'UUUUUUUUUUUUUUUU',_binary '\nh¯H\ÚMĞŸ·3Û >«',_binary '\nh¯H\ÚMĞŸ·3Û >«',0),(_binary 'UUUUUUUUUUUUUUUU',_binary 'ÿÂB=Eg‚&\Z\õz\ë',_binary 'ÿÂB=Eg‚&\Z\õz\ë',0),(_binary 'UUUUUUUUUUUUUUUU',_binary '\Z($\ò¦\rO0ƒ\ï\â\0\ó\Í\ì',_binary '\Z($\ò¦\rO0ƒ\ï\â\0\ó\Í\ì',0),(_binary 'UUUUUUUUUUUUUUUU',_binary '\Z($\ò¦\rO0ƒ\ï\â\0\ó\Í\ì',_binary '\Z\ëd\ÕR\ZA¿³U½Ö­‹¢',1),(_binary 'UUUUUUUUUUUUUUUU',_binary '\Z($\ò¦\rO0ƒ\ï\â\0\ó\Í\ì',_binary '\á\ášlCÌ©\ô\\\óU2²',1),(_binary 'UUUUUUUUUUUUUUUU',_binary '\Z\ëd\ÕR\ZA¿³U½Ö­‹¢',_binary '\Z\ëd\ÕR\ZA¿³U½Ö­‹¢',0),(_binary 'UUUUUUUUUUUUUUUU',_binary 'ù0¢\\@`¤\Ã\nœ\ë\"',_binary 'ù0¢\\@`¤\Ã\nœ\ë\"',0),(_binary 'UUUUUUUUUUUUUUUU',_binary 'ù0¢\\@`¤\Ã\nœ\ë\"',_binary '}³P\Ó\ÊQB­ƒ´w\Í<&|',1),(_binary 'UUUUUUUUUUUUUUUU',_binary 'iJr\áD¼²‰¥\Ë\ãW²',_binary 'iJr\áD¼²‰¥\Ë\ãW²',0),(_binary 'UUUUUUUUUUUUUUUU',_binary '1\ÜfCJ>§jO‡µ\Ë©',_binary 'ÿÂB=Eg‚&\Z\õz\ë',2),(_binary 'UUUUUUUUUUUUUUUU',_binary '1\ÜfCJ>§jO‡µ\Ë©',_binary 'iJr\áD¼²‰¥\Ë\ãW²',2),(_binary 'UUUUUUUUUUUUUUUU',_binary '1\ÜfCJ>§jO‡µ\Ë©',_binary '1\ÜfCJ>§jO‡µ\Ë©',0),(_binary 'UUUUUUUUUUUUUUUU',_binary '1\ÜfCJ>§jO‡µ\Ë©',_binary '‚1_I¶ˆ!rHt\ë”',1),(_binary 'UUUUUUUUUUUUUUUU',_binary '1\ÜfCJ>§jO‡µ\Ë©',_binary '‰º\Ë\÷.\ÄJ¡•uY£\É\ñª/',2),(_binary 'UUUUUUUUUUUUUUUU',_binary '1\ÜfCJ>§jO‡µ\Ë©',_binary '\ã•°Ö¢O\óƒ\\F._.',2),(_binary 'UUUUUUUUUUUUUUUU',_binary '4 tG[A\è½b4£—v',_binary '4 tG[A\è½b4£—v',0),(_binary 'UUUUUUUUUUUUUUUU',_binary 'M7™Œ\ßZF×½+\Èé®»“	',_binary 'M7™Œ\ßZF×½+\Èé®»“	',0),(_binary 'UUUUUUUUUUUUUUUU',_binary 'N‡4LŒ DU»A—YGZ',_binary 'N‡4LŒ DU»A—YGZ',0),(_binary 'UUUUUUUUUUUUUUUU',_binary 'P\åqgbL\n¡W\Ù77²¬',_binary 'P\åqgbL\n¡W\Ù77²¬',0),(_binary 'UUUUUUUUUUUUUUUU',_binary 'Vyƒ¢IK·§X‚‰V^',_binary 'M7™Œ\ßZF×½+\Èé®»“	',1),(_binary 'UUUUUUUUUUUUUUUU',_binary 'Vyƒ¢IK·§X‚‰V^',_binary 'Vyƒ¢IK·§X‚‰V^',0),(_binary 'UUUUUUUUUUUUUUUU',_binary 'Vyƒ¢IK·§X‚‰V^',_binary '\ËYú˜~@â‘®]%\Ş',1),(_binary 'UUUUUUUUUUUUUUUU',_binary 'm]\öXIÂ„a\ê	‰úù',_binary 'm]\öXIÂ„a\ê	‰úù',0),(_binary 'UUUUUUUUUUUUUUUU',_binary 'qpK¡wO¡¹ee$\ï',_binary 'qpK¡wO¡¹ee$\ï',0),(_binary 'UUUUUUUUUUUUUUUU',_binary 't…¯»eµK\"¨\ÍZ›‚•¬',_binary 't…¯»eµK\"¨\ÍZ›‚•¬',0),(_binary 'UUUUUUUUUUUUUUUU',_binary 't…¯»eµK\"¨\ÍZ›‚•¬',_binary 'Øƒ\ÒY\r¼JS¼°\0`¯$',1),(_binary 'UUUUUUUUUUUUUUUU',_binary '}³P\Ó\ÊQB­ƒ´w\Í<&|',_binary '}³P\Ó\ÊQB­ƒ´w\Í<&|',0),(_binary 'UUUUUUUUUUUUUUUU',_binary '³¢\ÂAx\ôAG\öˆœ',_binary '³¢\ÂAx\ôAG\öˆœ',0),(_binary 'UUUUUUUUUUUUUUUU',_binary '‚1_I¶ˆ!rHt\ë”',_binary 'ÿÂB=Eg‚&\Z\õz\ë',1),(_binary 'UUUUUUUUUUUUUUUU',_binary '‚1_I¶ˆ!rHt\ë”',_binary 'iJr\áD¼²‰¥\Ë\ãW²',1),(_binary 'UUUUUUUUUUUUUUUU',_binary '‚1_I¶ˆ!rHt\ë”',_binary '‚1_I¶ˆ!rHt\ë”',0),(_binary 'UUUUUUUUUUUUUUUU',_binary '‚1_I¶ˆ!rHt\ë”',_binary '‰º\Ë\÷.\ÄJ¡•uY£\É\ñª/',1),(_binary 'UUUUUUUUUUUUUUUU',_binary '‚1_I¶ˆ!rHt\ë”',_binary '\ã•°Ö¢O\óƒ\\F._.',1),(_binary 'UUUUUUUUUUUUUUUU',_binary '‰º\Ë\÷.\ÄJ¡•uY£\É\ñª/',_binary '‰º\Ë\÷.\ÄJ¡•uY£\É\ñª/',0),(_binary 'UUUUUUUUUUUUUUUU',_binary '\ËYú˜~@â‘®]%\Ş',_binary '\ËYú˜~@â‘®]%\Ş',0),(_binary 'UUUUUUUUUUUUUUUU',_binary '<W\êşH\ì¬\Ö\Ü\Ç\ã×…\ß',_binary '<W\êşH\ì¬\Ö\Ü\Ç\ã×…\ß',0),(_binary 'UUUUUUUUUUUUUUUU',_binary '£\óB)<JvŒÊ¹qƒ\ì',_binary '£\óB)<JvŒÊ¹qƒ\ì',0),(_binary 'UUUUUUUUUUUUUUUU',_binary '¦\Ğ\ğAıM@Ë\é\Óq~A\Ñc',_binary '¦\Ğ\ğAıM@Ë\é\Óq~A\Ñc',0),(_binary '',_binary 'ªªªªªªªªªªªªªªª¡',_binary 'ªªªªªªªªªªªªªªª¡',0),(_binary '\"\"\"\"\"\"\"\"\"\"\"\"\"\"\"\"',_binary 'ªªªªªªªªªªªªªªª¢',_binary 'ªªªªªªªªªªªªªªª¢',0),(_binary '3333333333333333',_binary 'ªªªªªªªªªªªªªªª£',_binary 'ªªªªªªªªªªªªªªª£',0),(_binary 'DDDDDDDDDDDDDDDD',_binary 'ªªªªªªªªªªªªªªª¤',_binary 'ªªªªªªªªªªªªªªª¤',0),(_binary 'UUUUUUUUUUUUUUUU',_binary 'ªªªªªªªªªªªªªªª¥',_binary 'n$ûOœB&‰©°c',1),(_binary 'UUUUUUUUUUUUUUUU',_binary 'ªªªªªªªªªªªªªªª¥',_binary '\nh¯H\ÚMĞŸ·3Û >«',2),(_binary 'UUUUUUUUUUUUUUUU',_binary 'ªªªªªªªªªªªªªªª¥',_binary 'ÿÂB=Eg‚&\Z\õz\ë',3),(_binary 'UUUUUUUUUUUUUUUU',_binary 'ªªªªªªªªªªªªªªª¥',_binary '\Z($\ò¦\rO0ƒ\ï\â\0\ó\Í\ì',2),(_binary 'UUUUUUUUUUUUUUUU',_binary 'ªªªªªªªªªªªªªªª¥',_binary '\Z\ëd\ÕR\ZA¿³U½Ö­‹¢',3),(_binary 'UUUUUUUUUUUUUUUU',_binary 'ªªªªªªªªªªªªªªª¥',_binary 'ù0¢\\@`¤\Ã\nœ\ë\"',1),(_binary 'UUUUUUUUUUUUUUUU',_binary 'ªªªªªªªªªªªªªªª¥',_binary 'iJr\áD¼²‰¥\Ë\ãW²',3),(_binary 'UUUUUUUUUUUUUUUU',_binary 'ªªªªªªªªªªªªªªª¥',_binary '1\ÜfCJ>§jO‡µ\Ë©',1),(_binary 'UUUUUUUUUUUUUUUU',_binary 'ªªªªªªªªªªªªªªª¥',_binary '4 tG[A\è½b4£—v',2),(_binary 'UUUUUUUUUUUUUUUU',_binary 'ªªªªªªªªªªªªªªª¥',_binary 'M7™Œ\ßZF×½+\Èé®»“	',3),(_binary 'UUUUUUUUUUUUUUUU',_binary 'ªªªªªªªªªªªªªªª¥',_binary 'N‡4LŒ DU»A—YGZ',1),(_binary 'UUUUUUUUUUUUUUUU',_binary 'ªªªªªªªªªªªªªªª¥',_binary 'P\åqgbL\n¡W\Ù77²¬',1),(_binary 'UUUUUUUUUUUUUUUU',_binary 'ªªªªªªªªªªªªªªª¥',_binary 'Vyƒ¢IK·§X‚‰V^',2),(_binary 'UUUUUUUUUUUUUUUU',_binary 'ªªªªªªªªªªªªªªª¥',_binary 'm]\öXIÂ„a\ê	‰úù',1),(_binary 'UUUUUUUUUUUUUUUU',_binary 'ªªªªªªªªªªªªªªª¥',_binary 'qpK¡wO¡¹ee$\ï',1),(_binary 'UUUUUUUUUUUUUUUU',_binary 'ªªªªªªªªªªªªªªª¥',_binary 't…¯»eµK\"¨\ÍZ›‚•¬',1),(_binary 'UUUUUUUUUUUUUUUU',_binary 'ªªªªªªªªªªªªªªª¥',_binary '}³P\Ó\ÊQB­ƒ´w\Í<&|',2),(_binary 'UUUUUUUUUUUUUUUU',_binary 'ªªªªªªªªªªªªªªª¥',_binary '³¢\ÂAx\ôAG\öˆœ',2),(_binary 'UUUUUUUUUUUUUUUU',_binary 'ªªªªªªªªªªªªªªª¥',_binary '‚1_I¶ˆ!rHt\ë”',2),(_binary 'UUUUUUUUUUUUUUUU',_binary 'ªªªªªªªªªªªªªªª¥',_binary '‰º\Ë\÷.\ÄJ¡•uY£\É\ñª/',3),(_binary 'UUUUUUUUUUUUUUUU',_binary 'ªªªªªªªªªªªªªªª¥',_binary '\ËYú˜~@â‘®]%\Ş',3),(_binary 'UUUUUUUUUUUUUUUU',_binary 'ªªªªªªªªªªªªªªª¥',_binary '<W\êşH\ì¬\Ö\Ü\Ç\ã×…\ß',1),(_binary 'UUUUUUUUUUUUUUUU',_binary 'ªªªªªªªªªªªªªªª¥',_binary '£\óB)<JvŒÊ¹qƒ\ì',1),(_binary 'UUUUUUUUUUUUUUUU',_binary 'ªªªªªªªªªªªªªªª¥',_binary '¦\Ğ\ğAıM@Ë\é\Óq~A\Ñc',1),(_binary 'UUUUUUUUUUUUUUUU',_binary 'ªªªªªªªªªªªªªªª¥',_binary 'ªªªªªªªªªªªªªªª¥',0),(_binary 'UUUUUUUUUUUUUUUU',_binary 'ªªªªªªªªªªªªªªª¥',_binary '­\\Œ\ñ;N\á\É$TĞ \Z',1),(_binary 'UUUUUUUUUUUUUUUU',_binary 'ªªªªªªªªªªªªªªª¥',_binary '­\ß\ÕkzHŒ\á\õ\ô/»:¾',1),(_binary 'UUUUUUUUUUUUUUUU',_binary 'ªªªªªªªªªªªªªªª¥',_binary '¿\á£=z,E\ïœ‹ÀW?}',1),(_binary 'UUUUUUUUUUUUUUUU',_binary 'ªªªªªªªªªªªªªªª¥',_binary 'ÅˆĞ£3\ÎL1›\İ\á\rK\á©\ò',1),(_binary 'UUUUUUUUUUUUUUUU',_binary 'ªªªªªªªªªªªªªªª¥',_binary '\ËBÙ¢e\ÒI\'‹¦\\\Èv‚',1),(_binary 'UUUUUUUUUUUUUUUU',_binary 'ªªªªªªªªªªªªªªª¥',_binary '\ÕgRFË#V\È\ØB\Í(',1),(_binary 'UUUUUUUUUUUUUUUU',_binary 'ªªªªªªªªªªªªªªª¥',_binary '×’Aİ»\ñ@‰ic±&2N',1),(_binary 'UUUUUUUUUUUUUUUU',_binary 'ªªªªªªªªªªªªªªª¥',_binary 'Øƒ\ÒY\r¼JS¼°\0`¯$',2),(_binary 'UUUUUUUUUUUUUUUU',_binary 'ªªªªªªªªªªªªªªª¥',_binary '\İ p,	\\H¥¥v\Ñ\è\'ª\Ş',1),(_binary 'UUUUUUUUUUUUUUUU',_binary 'ªªªªªªªªªªªªªªª¥',_binary '\Ş\ïs.üLÿ´%—\å uG',1),(_binary 'UUUUUUUUUUUUUUUU',_binary 'ªªªªªªªªªªªªªªª¥',_binary '\á\ášlCÌ©\ô\\\óU2²',3),(_binary 'UUUUUUUUUUUUUUUU',_binary 'ªªªªªªªªªªªªªªª¥',_binary '\ã•°Ö¢O\óƒ\\F._.',3),(_binary 'UUUUUUUUUUUUUUUU',_binary 'ªªªªªªªªªªªªªªª¥',_binary 'ò•†ŠV\ÆOÙ†]øs¢4',1),(_binary 'UUUUUUUUUUUUUUUU',_binary '­\\Œ\ñ;N\á\É$TĞ \Z',_binary '­\\Œ\ñ;N\á\É$TĞ \Z',0),(_binary 'UUUUUUUUUUUUUUUU',_binary '­\ß\ÕkzHŒ\á\õ\ô/»:¾',_binary '­\ß\ÕkzHŒ\á\õ\ô/»:¾',0),(_binary '7\àmZhŸC•»@\ál\Ö',_binary '®­zUZ\İCÚœ½\r\ÄWˆ',_binary '®­zUZ\İCÚœ½\r\ÄWˆ',0),(_binary 'UUUUUUUUUUUUUUUU',_binary '¿\á£=z,E\ïœ‹ÀW?}',_binary '¿\á£=z,E\ïœ‹ÀW?}',0),(_binary 'UUUUUUUUUUUUUUUU',_binary 'ÅˆĞ£3\ÎL1›\İ\á\rK\á©\ò',_binary 'ÅˆĞ£3\ÎL1›\İ\á\rK\á©\ò',0),(_binary 'UUUUUUUUUUUUUUUU',_binary '\ËBÙ¢e\ÒI\'‹¦\\\Èv‚',_binary '4 tG[A\è½b4£—v',1),(_binary 'UUUUUUUUUUUUUUUU',_binary '\ËBÙ¢e\ÒI\'‹¦\\\Èv‚',_binary '\ËBÙ¢e\ÒI\'‹¦\\\Èv‚',0),(_binary 'UUUUUUUUUUUUUUUU',_binary '\ÕgRFË#V\È\ØB\Í(',_binary '\ÕgRFË#V\È\ØB\Í(',0),(_binary 'UUUUUUUUUUUUUUUU',_binary '×’Aİ»\ñ@‰ic±&2N',_binary '×’Aİ»\ñ@‰ic±&2N',0),(_binary 'UUUUUUUUUUUUUUUU',_binary 'Øƒ\ÒY\r¼JS¼°\0`¯$',_binary 'Øƒ\ÒY\r¼JS¼°\0`¯$',0),(_binary 'UUUUUUUUUUUUUUUU',_binary '\İ p,	\\H¥¥v\Ñ\è\'ª\Ş',_binary '\nh¯H\ÚMĞŸ·3Û >«',1),(_binary 'UUUUUUUUUUUUUUUU',_binary '\İ p,	\\H¥¥v\Ñ\è\'ª\Ş',_binary '\Z($\ò¦\rO0ƒ\ï\â\0\ó\Í\ì',1),(_binary 'UUUUUUUUUUUUUUUU',_binary '\İ p,	\\H¥¥v\Ñ\è\'ª\Ş',_binary '\Z\ëd\ÕR\ZA¿³U½Ö­‹¢',2),(_binary 'UUUUUUUUUUUUUUUU',_binary '\İ p,	\\H¥¥v\Ñ\è\'ª\Ş',_binary '³¢\ÂAx\ôAG\öˆœ',1),(_binary 'UUUUUUUUUUUUUUUU',_binary '\İ p,	\\H¥¥v\Ñ\è\'ª\Ş',_binary '\İ p,	\\H¥¥v\Ñ\è\'ª\Ş',0),(_binary 'UUUUUUUUUUUUUUUU',_binary '\İ p,	\\H¥¥v\Ñ\è\'ª\Ş',_binary '\á\ášlCÌ©\ô\\\óU2²',2),(_binary 'UUUUUUUUUUUUUUUU',_binary '\Ş\ïs.üLÿ´%—\å uG',_binary '\Ş\ïs.üLÿ´%—\å uG',0),(_binary 'UUUUUUUUUUUUUUUU',_binary '\á\ášlCÌ©\ô\\\óU2²',_binary '\á\ášlCÌ©\ô\\\óU2²',0),(_binary 'UUUUUUUUUUUUUUUU',_binary '\ã•°Ö¢O\óƒ\\F._.',_binary '\ã•°Ö¢O\óƒ\\F._.',0),(_binary 'UUUUUUUUUUUUUUUU',_binary 'ò•†ŠV\ÆOÙ†]øs¢4',_binary 'ò•†ŠV\ÆOÙ†]øs¢4',0);
/*!40000 ALTER TABLE `pcd_directory_closure_table` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `pcd_directory_tree_table`
--

DROP TABLE IF EXISTS `pcd_directory_tree_table`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `pcd_directory_tree_table` (
  `node_id` binary(16) NOT NULL,
  `node_user_id` binary(16) NOT NULL COMMENT 'æ‰€å±ç”¨æˆ·ID',
  `node_parent_id` binary(16) DEFAULT NULL COMMENT 'çˆ¶èŠ‚ç‚¹ID',
  `node_name` varchar(200) NOT NULL COMMENT 'èŠ‚ç‚¹åç§°',
  `node_create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'èŠ‚ç‚¹åˆ›å»ºæ—¶é—´',
  `node_status` enum('lock','active','pending','trashed','deleted') DEFAULT 'active' COMMENT 'èŠ‚ç‚¹çŠ¶æ€',
  `node_space_id` binary(16) DEFAULT NULL COMMENT 'æ‰€å±ç©ºé—´ID',
  PRIMARY KEY (`node_id`),
  UNIQUE KEY `uk_directory_tree` (`node_id`,`node_user_id`,`node_parent_id`),
  KEY `node_user_id` (`node_user_id`),
  KEY `node_parent_id` (`node_parent_id`),
  KEY `idx_node_space` (`node_space_id`,`node_status`),
  CONSTRAINT `pcd_directory_tree_table_ibfk_1` FOREIGN KEY (`node_user_id`) REFERENCES `pcd_user_info_table` (`user_id`) ON DELETE CASCADE,
  CONSTRAINT `pcd_directory_tree_table_ibfk_2` FOREIGN KEY (`node_parent_id`) REFERENCES `pcd_directory_tree_table` (`node_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='èŠ‚ç‚¹ç›®å½•æ ‘è¡¨';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `pcd_directory_tree_table`
--

LOCK TABLES `pcd_directory_tree_table` WRITE;
/*!40000 ALTER TABLE `pcd_directory_tree_table` DISABLE KEYS */;
INSERT INTO `pcd_directory_tree_table` VALUES (_binary 'n$ûOœB&‰©°c',_binary 'UUUUUUUUUUUUUUUU',_binary 'ªªªªªªªªªªªªªªª¥','æ–¹å¼2ï¼šæ°¸ä¹…æ¿€æ´»è¡¥ä¸ï¼ˆé€‚åˆæœ€æ–°ç‰ˆæœ¬ï¼‰','2026-07-13 15:35:14','active',NULL),(_binary '\nh¯H\ÚMĞŸ·3Û >«',_binary 'UUUUUUUUUUUUUUUU',_binary '\İ p,	\\H¥¥v\Ñ\è\'ª\Ş','å…¬å…±è¯¾-æ•°å­¦ä¸å¤§æ•°æ®å­¦é™¢2025ç§‹è€ƒè¯•åŠå­¦ç”Ÿè€ƒåœºå®‰æ’','2026-07-06 10:39:42','active',NULL),(_binary 'ÿÂB=Eg‚&\Z\õz\ë',_binary 'UUUUUUUUUUUUUUUU',_binary '‚1_I¶ˆ!rHt\ë”','config-jetbrains','2026-07-13 15:35:52','active',NULL),(_binary '\Z($\ò¦\rO0ƒ\ï\â\0\ó\Í\ì',_binary 'UUUUUUUUUUUUUUUU',_binary '\İ p,	\\H¥¥v\Ñ\è\'ª\Ş','å¤§å­¦å¤–è¯­ç­¾åˆ°è¡¨ï¼ˆä¾›æŸ¥è¯¢è€ƒåœºä½¿ç”¨ï¼‰','2026-07-06 10:38:52','active',NULL),(_binary '\Z\ëd\ÕR\ZA¿³U½Ö­‹¢',_binary 'UUUUUUUUUUUUUUUU',_binary '\Z($\ò¦\rO0ƒ\ï\â\0\ó\Í\ì','å¤§å­¦è‹±è¯­Bï¼ˆ1ï¼‰','2026-07-06 10:39:01','active',NULL),(_binary 'ù0¢\\@`¤\Ã\nœ\ë\"',_binary 'UUUUUUUUUUUUUUUU',_binary 'ªªªªªªªªªªªªªªª¥','img','2026-07-05 10:02:55','lock',NULL),(_binary 'iJr\áD¼²‰¥\Ë\ãW²',_binary 'UUUUUUUUUUUUUUUU',_binary '‚1_I¶ˆ!rHt\ë”','scripts','2026-07-13 15:36:01','active',NULL),(_binary '1\ÜfCJ>§jO‡µ\Ë©',_binary 'UUUUUUUUUUUUUUUU',_binary 'ªªªªªªªªªªªªªªª¥','æ–¹å¼3ï¼šæ°¸ä¹…æ¿€æ´»è¡¥ä¸+è„šæœ¬ï¼ˆé€‚åˆæœ€æ–°ç‰ˆæœ¬ï¼Œå¯æ˜¾ç¤ºåˆ°2025å¹´ï¼‰','2026-07-13 15:35:45','active',NULL),(_binary '4 tG[A\è½b4£—v',_binary 'UUUUUUUUUUUUUUUU',_binary '\ËBÙ¢e\ÒI\'‹¦\\\Èv‚','tests','2026-06-14 09:57:10','active',NULL),(_binary 'M7™Œ\ßZF×½+\Èé®»“	',_binary 'UUUUUUUUUUUUUUUU',_binary 'Vyƒ¢IK·§X‚‰V^','config','2026-07-13 15:35:35','active',NULL),(_binary 'N‡4LŒ DU»A—YGZ',_binary 'UUUUUUUUUUUUUUUU',_binary 'ªªªªªªªªªªªªªªª¥','plugins-jetbrain','2026-07-05 14:09:07','active',NULL),(_binary 'P\åqgbL\n¡W\Ù77²¬',_binary 'UUUUUUUUUUUUUUUU',_binary 'ªªªªªªªªªªªªªªª¥','docs','2026-07-13 11:19:15','active',NULL),(_binary 'Vyƒ¢IK·§X‚‰V^',_binary 'UUUUUUUUUUUUUUUU',_binary 'n$ûOœB&‰©°c','ja-netfilter','2026-07-13 15:35:19','active',NULL),(_binary 'm]\öXIÂ„a\ê	‰úù',_binary 'UUUUUUUUUUUUUUUU',_binary 'ªªªªªªªªªªªªªªª¥','o0','2026-07-14 21:07:30','active',NULL),(_binary 'qpK¡wO¡¹ee$\ï',_binary 'UUUUUUUUUUUUUUUU',_binary 'ªªªªªªªªªªªªªªª¥','9','2026-06-13 17:09:14','deleted',NULL),(_binary 't…¯»eµK\"¨\ÍZ›‚•¬',_binary 'UUUUUUUUUUUUUUUU',_binary 'ªªªªªªªªªªªªªªª¥','Car-4G','2026-07-07 11:12:05','active',NULL),(_binary '}³P\Ó\ÊQB­ƒ´w\Í<&|',_binary 'UUUUUUUUUUUUUUUU',_binary 'ù0¢\\@`¤\Ã\nœ\ë\"','34324','2026-07-13 12:32:06','active',NULL),(_binary '³¢\ÂAx\ôAG\öˆœ',_binary 'UUUUUUUUUUUUUUUU',_binary '\İ p,	\\H¥¥v\Ñ\è\'ª\Ş','å…¬å…±è¯¾ é©¬å…‹æ€ä¸»ä¹‰å­¦é™¢ 2025å¹´ç§‹å­£å­¦æœŸ è€ƒåœºè€ƒè¯•å®‰æ’è€ƒè¯•ç­¾åˆ°è¡¨ï¼ˆå­¦ç”ŸæŸ¥è¯¢æ•™å®¤ç”¨ï¼‰','2026-07-06 10:39:31','active',NULL),(_binary '‚1_I¶ˆ!rHt\ë”',_binary 'UUUUUUUUUUUUUUUU',_binary '1\ÜfCJ>§jO‡µ\Ë©','jetbra','2026-07-13 15:35:47','active',NULL),(_binary '‰º\Ë\÷.\ÄJ¡•uY£\É\ñª/',_binary 'UUUUUUUUUUUUUUUU',_binary '‚1_I¶ˆ!rHt\ë”','vmoptions','2026-07-13 15:36:18','active',NULL),(_binary '\ËYú˜~@â‘®]%\Ş',_binary 'UUUUUUUUUUUUUUUU',_binary 'Vyƒ¢IK·§X‚‰V^','plugins','2026-07-13 15:35:24','active',NULL),(_binary '<W\êşH\ì¬\Ö\Ü\Ç\ã×…\ß',_binary 'UUUUUUUUUUUUUUUU',_binary 'ªªªªªªªªªªªªªªª¥','data','2026-06-14 09:57:27','deleted',NULL),(_binary '£\óB)<JvŒÊ¹qƒ\ì',_binary 'UUUUUUUUUUUUUUUU',_binary 'ªªªªªªªªªªªªªªª¥','reset_script','2026-07-13 15:37:21','active',NULL),(_binary '¦\Ğ\ğAıM@Ë\é\Óq~A\Ñc',_binary 'UUUUUUUUUUUUUUUU',_binary 'ªªªªªªªªªªªªªªª¥','XSWEDF@','2026-07-05 19:45:26','active',NULL),(_binary 'ªªªªªªªªªªªªªªª¡',_binary '',NULL,'root','2026-06-10 14:52:38','active',NULL),(_binary 'ªªªªªªªªªªªªªªª¢',_binary '\"\"\"\"\"\"\"\"\"\"\"\"\"\"\"\"',NULL,'root','2026-06-10 14:52:38','active',NULL),(_binary 'ªªªªªªªªªªªªªªª£',_binary '3333333333333333',NULL,'root','2026-06-10 14:52:38','active',NULL),(_binary 'ªªªªªªªªªªªªªªª¤',_binary 'DDDDDDDDDDDDDDDD',NULL,'root','2026-06-10 14:52:38','active',NULL),(_binary 'ªªªªªªªªªªªªªªª¥',_binary 'UUUUUUUUUUUUUUUU',NULL,'root','2026-06-10 14:52:38','lock',NULL),(_binary '­\\Œ\ñ;N\á\É$TĞ \Z',_binary 'UUUUUUUUUUUUUUUU',_binary 'ªªªªªªªªªªªªªªª¥','vmoptions','2026-07-05 14:08:50','active',NULL),(_binary '­\ß\ÕkzHŒ\á\õ\ô/»:¾',_binary 'UUUUUUUUUUUUUUUU',_binary 'ªªªªªªªªªªªªªªª¥','config-jetbrains','2026-07-05 14:08:30','active',NULL),(_binary '®­zUZ\İCÚœ½\r\ÄWˆ',_binary '7\àmZhŸC•»@\ál\Ö',NULL,'#root','2026-06-21 15:30:30','active',NULL),(_binary '¿\á£=z,E\ïœ‹ÀW?}',_binary 'UUUUUUUUUUUUUUUU',_binary 'ªªªªªªªªªªªªªªª¥','æ–¹å¼1ï¼šæ— é™é‡ç½®30å¤©è¯•ç”¨æœŸè¡¥ä¸ï¼ˆé€‚åˆè€ç‰ˆæœ¬ï¼‰','2026-07-13 15:37:19','active',NULL),(_binary 'ÅˆĞ£3\ÎL1›\İ\á\rK\á©\ò',_binary 'UUUUUUUUUUUUUUUU',_binary 'ªªªªªªªªªªªªªªª¥','scripts','2026-07-05 14:08:37','active',NULL),(_binary '\ËBÙ¢e\ÒI\'‹¦\\\Èv‚',_binary 'UUUUUUUUUUUUUUUU',_binary 'ªªªªªªªªªªªªªªª¥','hello','2026-06-14 09:57:15','deleted',NULL),(_binary '\ÕgRFË#V\È\ØB\Í(',_binary 'UUUUUUUUUUUUUUUU',_binary 'ªªªªªªªªªªªªªªª¥','111111111','2026-06-14 09:57:19','deleted',NULL),(_binary '×’Aİ»\ñ@‰ic±&2N',_binary 'UUUUUUUUUUUUUUUU',_binary 'ªªªªªªªªªªªªªªª¥','7777','2026-07-05 07:35:24','lock',NULL),(_binary 'Øƒ\ÒY\r¼JS¼°\0`¯$',_binary 'UUUUUUUUUUUUUUUU',_binary 't…¯»eµK\"¨\ÍZ›‚•¬','__pycache__','2026-07-07 11:12:16','active',NULL),(_binary '\İ p,	\\H¥¥v\Ñ\è\'ª\Ş',_binary 'UUUUUUUUUUUUUUUU',_binary 'ªªªªªªªªªªªªªªª¥','02-å…¬å…±è¯¾çš„æœŸæœ«è€ƒè¯•å®‰æ’-å‘ç»™å­¦ç”Ÿ','2026-07-06 10:38:47','active',NULL),(_binary '\Ş\ïs.üLÿ´%—\å uG',_binary 'UUUUUUUUUUUUUUUU',_binary 'ªªªªªªªªªªªªªªª¥','32322','2026-07-07 11:32:20','active',NULL),(_binary '\á\ášlCÌ©\ô\\\óU2²',_binary 'UUUUUUUUUUUUUUUU',_binary '\Z($\ò¦\rO0ƒ\ï\â\0\ó\Í\ì','å¤§å­¦è‹±è¯­Aï¼ˆ1ï¼‰','2026-07-06 10:39:24','active',NULL),(_binary '\ã•°Ö¢O\óƒ\\F._.',_binary 'UUUUUUUUUUUUUUUU',_binary '‚1_I¶ˆ!rHt\ë”','plugins-jetbrains','2026-07-13 15:37:08','active',NULL),(_binary 'ò•†ŠV\ÆOÙ†]øs¢4',_binary 'UUUUUUUUUUUUUUUU',_binary 'ªªªªªªªªªªªªªªª¥','.openapi-generator','2026-07-13 11:19:11','active',NULL);
/*!40000 ALTER TABLE `pcd_directory_tree_table` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `pcd_file_info_table`
--

DROP TABLE IF EXISTS `pcd_file_info_table`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `pcd_file_info_table` (
  `file_name` varchar(150) NOT NULL COMMENT 'æ–‡ä»¶åç§°',
  `file_uploaded_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'æ–‡ä»¶ä¸Šä¼ æ—¶é—´',
  `file_size` bigint NOT NULL COMMENT 'æ–‡ä»¶å¤§å°',
  `file_type` varchar(120) NOT NULL COMMENT 'æ–‡ä»¶ç±»å‹',
  `file_author_id` binary(16) NOT NULL COMMENT 'æ–‡ä»¶ä½œè€…ID',
  `file_id` binary(16) NOT NULL,
  `file_checksum` varchar(256) NOT NULL COMMENT 'æ–‡ä»¶æ ¡éªŒå€¼',
  `file_total_chunks` int NOT NULL COMMENT 'æ–‡ä»¶åˆ‡ç‰‡æ•°ç›®',
  `file_node_id` binary(16) NOT NULL COMMENT 'æ–‡ä»¶æ‰€åœ¨ç›®å½•èŠ‚ç‚¹ID',
  `file_storage_path` varchar(512) DEFAULT NULL COMMENT 'æ–‡ä»¶å­˜å‚¨è·¯å¾„',
  `file_status` enum('active','deleted','trashed','merging','merged','merge_failed','scanning','scan_failed','reject') NOT NULL DEFAULT 'active' COMMENT 'æ–‡ä»¶çŠ¶æ€',
  `file_space_id` binary(16) DEFAULT NULL COMMENT 'æ‰€å±ç©ºé—´ID',
  PRIMARY KEY (`file_id`),
  UNIQUE KEY `uk_file_info` (`file_id`,`file_author_id`,`file_node_id`),
  KEY `file_author_id` (`file_author_id`),
  KEY `fk_file_info_directory_tree` (`file_node_id`),
  KEY `idx_file_space` (`file_space_id`,`file_status`),
  CONSTRAINT `fk_file_info_directory_tree` FOREIGN KEY (`file_node_id`) REFERENCES `pcd_directory_tree_table` (`node_id`) ON DELETE CASCADE,
  CONSTRAINT `pcd_file_info_table_ibfk_1` FOREIGN KEY (`file_author_id`) REFERENCES `pcd_user_info_table` (`user_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='æ–‡ä»¶ä¿¡æ¯è¡¨';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `pcd_file_info_table`
--

LOCK TABLES `pcd_file_info_table` WRITE;
/*!40000 ALTER TABLE `pcd_file_info_table` DISABLE KEYS */;
INSERT INTO `pcd_file_info_table` VALUES ('CarContext.py','2026-07-07 11:12:33',475,'text/x-python-script',_binary 'UUUUUUUUUUUUUUUU',_binary '\0\'M\óI“´­Æ§UE','dc742955923cd2dccb2f4011a87828a43b8a62b8750e7ca8521e4f1e1b0eb012',1,_binary 't…¯»eµK\"¨\ÍZ›‚•¬','../Uploads/storage/bef769a0-2266-4467-ae38-b29822e086ef-1.cloud','active',NULL),('UploadAvatarRequest.md','2026-07-13 11:21:58',493,'text/markdown',_binary 'UUUUUUUUUUUUUUUU',_binary '4#TF+¢%Ÿ`\Ù/Q','32ce91c5fb92cb870b2dcde36c8c33d3ff6d409a8a273344bc5131780fb48c70',1,_binary 'P\åqgbL\n¡W\Ù77²¬','../Uploads/storage/88d897f9-8ae8-4995-9f8d-5bf1993a63bd-1.cloud','active',NULL),('FileControllerApi.md','2026-07-13 11:20:30',11702,'text/markdown',_binary 'UUUUUUUUUUUUUUUU',_binary '’G\Ó\ìJ$²›FaPº…\ï','52f56bffef029258777b1fb0b428106feb50fb85d8b464a40151a91b806b1221',1,_binary 'P\åqgbL\n¡W\Ù77²¬','../Uploads/storage/941cca86-1979-42a5-811d-967d43ccd12e-1.cloud','active',NULL),('rubymine.vmoptions','2026-07-13 15:36:29',635,'application/octet-stream',_binary 'UUUUUUUUUUUUUUUU',_binary '™£†H¢@¸‹N‡\òxù','58b9de329a0a3786ca2b531abb2b66e91e78e475458faa284133b8fb92938cee',1,_binary '‰º\Ë\÷.\ÄJ¡•uY£\É\ñª/','../Uploads/storage/acccc3f1-826a-4460-9d34-47e91f82472c-1.cloud','active',NULL),('uninstall-current-user.vbs','2026-07-05 14:08:43',749,'application/octet-stream',_binary 'UUUUUUUUUUUUUUUU',_binary 'ÿ%*\ÇN¤¯6Ÿ\Øf\ær','2f9a8e832664bacd9ca9bd3504a0df4e8b6abce9fa153f22c0bbf8192d114fb6',1,_binary 'ÅˆĞ£3\ÎL1›\İ\á\rK\á©\ò','../Uploads/storage/a9e4f51a-e3bb-4f98-8a8e-e2915c9d18c5-1.cloud','active',NULL),('MoveNodeRequest.md','2026-07-13 11:21:03',501,'text/markdown',_binary 'UUUUUUUUUUUUUUUU',_binary '\àºN½	Ccˆ\Ú\ŞY;\õY\n','070bbc03b4cbce0413bce68b5a040e936a21460ae00c25a9c69d734493d6f35e',1,_binary 'P\åqgbL\n¡W\Ù77²¬','../Uploads/storage/310de884-d6b8-41d8-adec-139262f42202-1.cloud','active',NULL),('å¤§å­¦æ—¥è¯­.xls','2026-07-06 10:38:57',36352,'application/vnd.ms-excel',_binary 'UUUUUUUUUUUUUUUU',_binary '	xw‘;O:”6\ğ†j—m','071d992399e7d630cf1532be58040703f70b0f9063066981033d102ff7f55abb',1,_binary '\Z($\ò¦\rO0ƒ\ï\â\0\ó\Í\ì','../Uploads/storage/5f2cd107-0913-44a9-a311-40fa4a33c398-1.cloud','active',NULL),('QuotaVO.md','2026-07-13 11:21:27',978,'text/markdown',_binary 'UUUUUUUUUUUUUUUU',_binary '	©\r´À=H¤š#0œ«{','5238f00f797ccf6e214b361e79efbf8649987d89726cbdb450240e344cfb0913',1,_binary 'P\åqgbL\n¡W\Ù77²¬','../Uploads/storage/9f480ff3-a931-49aa-b08b-8d919b405a87-1.cloud','active',NULL),('UserManageControllerApi.md','2026-07-13 11:20:07',8590,'text/markdown',_binary 'UUUUUUUUUUUUUUUU',_binary '\n+\Ò\ì\å\ÕG­«¹€O™x,','67c116c0cf522656c50ac956c22b60038221d4eefcc4786108756317d4e75193',1,_binary 'P\åqgbL\n¡W\Ù77²¬','../Uploads/storage/b4e31a57-1c9c-40b7-8088-3c222ed05cb5-1.cloud','active',NULL),('README.md','2026-07-05 10:02:50',989,'text/markdown',_binary 'UUUUUUUUUUUUUUUU',_binary '\ÊX¯\ò@‹ƒ0\Ù\å\Ê\Ø','97e1500e33a4a08e27512e33e8279d64740e400f4ce4923018d7cb4d80142f7d',1,_binary 'ªªªªªªªªªªªªªªª¥','../Uploads/storage/55816620-97b5-4e3b-8410-2f849c7ecc8e-1.cloud','active',NULL),('install-all-users.vbs','2026-07-05 14:08:37',2426,'application/octet-stream',_binary 'UUUUUUUUUUUUUUUU',_binary '\rÈ»°|2C=‰\'\î\ó+','26a6ab6fa87ade5e2384bd539bcd8f01e9400b3ab636de9843c92b8099c96493',1,_binary 'ÅˆĞ£3\ÎL1›\İ\á\rK\á©\ò','../Uploads/storage/6dd72a21-5b48-4d7b-ae4a-46364a512c04-1.cloud','active',NULL),('JsonResultFileVO.md','2026-07-13 11:21:25',654,'text/markdown',_binary 'UUUUUUUUUUUUUUUU',_binary 'Â¸)\Ï\ÉHØµ\0ú\åX¹O','af780f272ead4512b4236050e70821ab05a4ca114350aa1dd11ab9537fe8fdc0',1,_binary 'P\åqgbL\n¡W\Ù77²¬','../Uploads/storage/0bc305dc-9b5a-437f-8f3b-45f41ce9c813-1.cloud','active',NULL),('dns.conf','2026-07-13 15:35:53',49,'application/octet-stream',_binary 'UUUUUUUUUUUUUUUU',_binary ']\ô^v1H£«e	\Õ	y','b9be9c700786b411395a66def0b259670074c32bced59b33be2ddd486abcbe06',1,_binary 'ÿÂB=Eg‚&\Z\õz\ë','../Uploads/storage/8e6c17a0-d376-4c9e-8976-46149ca82458-1.cloud','active',NULL),('å¤§å­¦å¤–è¯­-2025å¹´ç§‹å­£å­¦æœŸæœŸæœ«è€ƒè¯•æ’è€ƒå®‰æ’è¡¨.xlsx','2026-07-06 10:38:59',15843,'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',_binary 'UUUUUUUUUUUUUUUU',_binary '\ö’¯\éGü”\Ï#^\Ä\öy','e96900403bab57445afcfcf274595f4055a9d80462c20a3fe8268ee89ef0553d',1,_binary '\Z($\ò¦\rO0ƒ\ï\â\0\ó\Í\ì','../Uploads/storage/ec6eb8c9-8e56-4d6c-b0eb-c5300568144c-1.cloud','active',NULL),('å¤§å­¦è‹±è¯­Aï¼ˆ1ï¼‰ç¾æœ¯.xls','2026-07-06 10:39:24',82944,'application/vnd.ms-excel',_binary 'UUUUUUUUUUUUUUUU',_binary '¼¸`ÇµOH¨0\çxú®\İ','aba54a00aded432479e748166b715714d201f5d993abef019ec0f99ba5b10006',1,_binary '\á\ášlCÌ©\ô\\\óU2²','../Uploads/storage/135f8e11-6456-4127-a164-bc3b2c36e735-1.cloud','active',NULL),('æ³¨æ„ï¼šå°†æ–‡ä»¶å¤¹æ•´ä¸ªæ‹·è´åˆ°æŸä¸ªä½ç½®ï¼Œå°±ä¸è¦åŠ¨äº†.txt','2026-07-13 15:35:17',0,'text/plain',_binary 'UUUUUUUUUUUUUUUU',_binary '¬\Æ.\ëB±\r¤L@‚\Î)','e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855',1,_binary 'n$ûOœB&‰©°c','../Uploads/storage/dc5b9c37-775e-43d9-940d-8ddd3c63a611-1.cloud','active',NULL),('JsonResultFolderNodeVO.md','2026-07-13 11:19:44',684,'text/markdown',_binary 'UUUUUUUUUUUUUUUU',_binary '\æt°n@p²6ƒÊ‡”)B','1ec45aa743552d62f3676f5f92c656060d16771634d9946b94df875499c198f5',1,_binary 'P\åqgbL\n¡W\Ù77²¬','../Uploads/storage/ef909c46-fb3b-49df-a9d5-8bb3f658a2f1-1.cloud','active',NULL),('QuotaControllerApi.md','2026-07-13 11:19:40',1372,'text/markdown',_binary 'UUUUUUUUUUUUUUUU',_binary '\Ømü\"@µµ\ïY`¬aœ\É','6b78bc04e7c9d94ea68b293eb00e4b62338d3f28c95e1df26cdb78ec9722503a',1,_binary 'P\åqgbL\n¡W\Ù77²¬','../Uploads/storage/79be70f6-682f-4872-93db-228e76f48bda-1.cloud','active',NULL),('ä¸ªäººå­¦ä¹ è·¯çº¿.md','2026-07-08 19:13:01',902,'text/markdown',_binary 'UUUUUUUUUUUUUUUU',_binary 'n=»r)BQ{`\æµ(\Õ','62fb7c8cbce28dfab5757297d6c70c284790e865746103a3c6c11aa12c9bc564',1,_binary 'ªªªªªªªªªªªªªªª¥','../Uploads/storage/5ab528a0-4625-4b6a-a760-4ce39ab089aa-1.cloud','active',NULL),('idea.vmoptions','2026-07-05 14:09:00',702,'application/octet-stream',_binary 'UUUUUUUUUUUUUUUU',_binary '\Ê\Ù\ëºM\ê¢A›£‚Å»','d55134899ca9c5786d5cbee1751f34dfcaee305b1eab591c0c5ad30304a62b5f',1,_binary '­\\Œ\ñ;N\á\É$TĞ \Z','../Uploads/storage/e5dbc907-4244-4ba2-9e15-cd208de385ee-1.cloud','active',NULL),('CarActionModel.py','2026-07-07 11:12:10',5778,'text/x-python-script',_binary 'UUUUUUUUUUUUUUUU',_binary '\Ï0-\ó\õLÜ‚d¥[\æş\í','e98050ec68c6d8c6010067d984051b18d0d9c45e0cd8af9eec7a28b394bd2e7c',1,_binary 't…¯»eµK\"¨\ÍZ›‚•¬','../Uploads/storage/0246f8bc-d314-4130-85cf-e6dc014fd75b-1.cloud','active',NULL),('mymap.conf','2026-07-13 15:35:40',97,'application/octet-stream',_binary 'UUUUUUUUUUUUUUUU',_binary 'DµLÇµMhºWp±»','289d6a050ea8bf6747a4dc6d8d8103fe62f899f044f669636922aae8c18ce3c3',1,_binary 'M7™Œ\ßZF×½+\Èé®»“	','../Uploads/storage/eb42ddbd-24fe-4d31-a809-908b7c9171de-1.cloud','active',NULL),('JsonResultListLoginDeviceVO.md','2026-07-13 11:19:35',714,'text/markdown',_binary 'UUUUUUUUUUUUUUUU',_binary 'I‰\ò‘¨B0½•š5‰¬','0620277409f6d425d96919a39789d74b1fb0b17e5031c6256e61134f9e2e347b',1,_binary 'P\åqgbL\n¡W\Ù77²¬','../Uploads/storage/59bbebbe-b2cc-4130-a6d3-6545d9a1eaec-1.cloud','active',NULL),('goland.vmoptions','2026-07-13 15:36:58',634,'application/octet-stream',_binary 'UUUUUUUUUUUUUUUU',_binary 'ú^TSvCTª®K{T\Û;ù','c03bf5ae617eae087cf21a339dd956ce1260d11863e60a21b8b0365f4afdc7d5',1,_binary '‰º\Ë\÷.\ÄJ¡•uY£\É\ñª/','../Uploads/storage/c762d826-bd38-43d9-820c-ee1540fb3aa0-1.cloud','active',NULL),('FileVO.md','2026-07-13 11:21:00',925,'text/markdown',_binary 'UUUUUUUUUUUUUUUU',_binary '\Z•5Ÿ\ÍoJÚ­\0>s»\ÄV','8f94e63ba73cd1f88617f6bf79a6033d7a4999186c4b23f010887fcbe8fa4dda',1,_binary 'P\åqgbL\n¡W\Ù77²¬','../Uploads/storage/633f4811-f4d0-45b8-9cd4-94289179d2db-1.cloud','active',NULL),('ShareControllerApi.md','2026-07-13 11:19:53',12775,'text/markdown',_binary 'UUUUUUUUUUUUUUUU',_binary '(„\ñy\rFy£·.«´+','d85ccccc9de5d1439079c0df3274ceb2a4b7e8d3f6382d1cec59bd9082f417c6',1,_binary 'P\åqgbL\n¡W\Ù77²¬','../Uploads/storage/acfbe405-e645-46bc-be46-175c7c2dccc7-1.cloud','active',NULL),('url.conf','2026-07-13 15:35:59',74,'application/octet-stream',_binary 'UUUUUUUUUUUUUUUU',_binary '–?\ĞLVI\òŸEc\èV\Ï\Ç\ñ','88e1dca8019ad412cf2c6fbd947a83786cffc7b32f1ee35594d25d1f38fae5f8',1,_binary 'ÿÂB=Eg‚&\Z\õz\ë','../Uploads/storage/0ecaf361-cedb-4ac0-ab91-72eb67f50440-1.cloud','active',NULL),('install-current-user.vbs','2026-07-13 15:36:16',1811,'application/octet-stream',_binary 'UUUUUUUUUUUUUUUU',_binary '¤\ïLÖ»@ğ·‘Œ¸±$04','4ed1b665c259991966001d048818a64cd7f3202faf0346c414a6d18c3be2ace0',1,_binary 'iJr\áD¼²‰¥\Ë\ãW²','../Uploads/storage/d9839058-44a8-4402-bae2-4db1401d6405-1.cloud','active',NULL),('é™„ä»¶2ï¼š2025å¹´ç§‹å­£å­¦æœŸæœŸæœ«è€ƒè¯•å®‰æ’è¡¨ï¼ˆæ•°å­¦ä¸å¤§æ•°æ®å­¦é™¢ï¼‰ - - çº¿æ€§ä»£æ•°.xlsx','2026-07-06 10:39:55',23389,'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',_binary 'UUUUUUUUUUUUUUUU',_binary '¼c7…\ßA7¡<\ì3‰	1@','4b7a857a886c780777cdbe8ba3f3e1eba89b1ea3ca647f114115380b3c5864db',1,_binary '\nh¯H\ÚMĞŸ·3Û >«','../Uploads/storage/73ac9e8a-43a6-4477-830d-fdf8a774408d-1.cloud','active',NULL),('power.conf','2026-07-13 15:35:38',7487,'application/octet-stream',_binary 'UUUUUUUUUUUUUUUU',_binary '\r\î†t\ĞIº†˜yX\Ç','5314860eae776e150d88f7869ccaa0d21ed281430cbf6de38a49358eb0f7b625',1,_binary 'M7™Œ\ßZF×½+\Èé®»“	','../Uploads/storage/8a88edcc-8e0d-4e01-b072-2b2a9246ed1f-1.cloud','active',NULL),('dns.conf','2026-07-13 15:35:36',67,'application/octet-stream',_binary 'UUUUUUUUUUUUUUUU',_binary ' g\ÅÀ%TIø.K¤`Ö£\Õ','f098e501d16f260fc7fced68935bb63e52bddb660da3170edb5bc5026d6145c4',1,_binary 'M7™Œ\ßZF×½+\Èé®»“	','../Uploads/storage/ddaca184-d370-498e-8ac7-866127b86f55-1.cloud','active',NULL),('01-2025ç§‹æœŸæœ«è€ƒè¯•å®‰æ’åŠ ä¸Šå¤–æ–‡çš„å®Œæ•´ç‰ˆ-å‘ç»™å­¦ç”Ÿ.xlsx','2026-07-06 10:38:43',223007,'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',_binary 'UUUUUUUUUUUUUUUU',_binary ' x!\Ôd?I³¦\ï\óÿÿ@?','1df486f08a0581cdab3eb345ae6016f4abc6991c88a1e5b4782443171bf9de85',1,_binary 'ªªªªªªªªªªªªªªª¥','../Uploads/storage/f0e7541d-70e6-4c8c-a2b3-dca7e5f16d58-1.cloud','active',NULL),('dataspell.vmoptions','2026-07-13 15:36:45',634,'application/octet-stream',_binary 'UUUUUUUUUUUUUUUU',_binary '!\ä‹|‚C?\Õ;/\Ôfª','c03bf5ae617eae087cf21a339dd956ce1260d11863e60a21b8b0365f4afdc7d5',1,_binary '‰º\Ë\÷.\ÄJ¡•uY£\É\ñª/','../Uploads/storage/537ebc48-6d30-443d-a09c-e5ea641f8a4f-1.cloud','active',NULL),('TrashTargetVO.md','2026-07-13 11:19:46',1170,'text/markdown',_binary 'UUUUUUUUUUUUUUUU',_binary '!·`\Ü\Ô I!«\ÍÁ	Lø','e27c3e44ae5b300dd31a8e94d11e9d17cc095fe4e03a6ec8d9e221ae02e6ed95',1,_binary 'P\åqgbL\n¡W\Ù77²¬','../Uploads/storage/67f2e749-bd2f-4de5-8227-31b5fde6dd0d-1.cloud','active',NULL),('connect.py','2026-06-14 18:22:32',1536,'text/x-python-script',_binary 'UUUUUUUUUUUUUUUU',_binary '\"2[\'©F§’â‹¤€u\ã','8b0f6bd56051930131e764b70e501cbcaeb6c36bc1afc975c6ec386169113009',1,_binary 'ªªªªªªªªªªªªªªª¥',NULL,'merging',NULL),('install-current-user.vbs','2026-07-05 14:08:48',1811,'application/octet-stream',_binary 'UUUUUUUUUUUUUUUU',_binary '#\n ¯/cC\\¦,\ÛÁ\Ø\à','4ed1b665c259991966001d048818a64cd7f3202faf0346c414a6d18c3be2ace0',1,_binary 'ÅˆĞ£3\ÎL1›\İ\á\rK\á©\ò','../Uploads/storage/35920e1a-41bd-4c67-87d0-a58c75146dfa-1.cloud','active',NULL),('ChangeUserPasswordRequest.md','2026-07-13 11:19:31',603,'text/markdown',_binary 'UUUUUUUUUUUUUUUU',_binary '#>D_Y\ĞE—\ë\çK¦°E\Ù','ed1b6bf1ca6558c95a5d9761cb14f90965eb60e5a11977fdc50ca0485ff3ba2c',1,_binary 'P\åqgbL\n¡W\Ù77²¬','../Uploads/storage/6369a43e-9312-49ac-b564-06052888a520-1.cloud','active',NULL),('WebRTC.png','2026-07-08 19:22:11',141955,'image/png',_binary 'UUUUUUUUUUUUUUUU',_binary '%s\Û\'0§OÍ¹X‡›z„û','77d32a1bc42d8254992e5b5f762183d41f17442718ee066093ed94a2d728a1c2',1,_binary 'ªªªªªªªªªªªªªªª¥','../Uploads/storage/e6743eca-11ae-43b9-8b4e-e57a9101c7f5-1.cloud','active',NULL),('studio.vmoptions','2026-07-05 14:09:02',702,'application/octet-stream',_binary 'UUUUUUUUUUUUUUUU',_binary '%\Æ2Ä­±@Í¸<\ì\á›\ğ\ğ8','84a349d678b98359cc6fb13dd63c0cf3b790bce13f6f2b2284726b70386b13f6',1,_binary '­\\Œ\ñ;N\á\É$TĞ \Z','../Uploads/storage/385c5442-3818-4859-b817-b4e1d1b51f6c-1.cloud','active',NULL),('CarEvent.cpython-310.pyc','2026-07-07 11:12:21',1197,'application/octet-stream',_binary 'UUUUUUUUUUUUUUUU',_binary '(uRN­ˆ;´\Í@€','6f6bbbc1cc12eb1883938659971213497ca9b46269140c16d05e1ccc55a92209',1,_binary 'Øƒ\ÒY\r¼JS¼°\0`¯$','../Uploads/storage/9610d418-470d-4115-821e-fd3c556791b8-1.cloud','active',NULL),('webstorm.vmoptions','2026-07-13 15:36:19',635,'application/octet-stream',_binary 'UUUUUUUUUUUUUUUU',_binary '(\Ø\Ë8›\ÆMÑ·qÁ\â','58b9de329a0a3786ca2b531abb2b66e91e78e475458faa284133b8fb92938cee',1,_binary '‰º\Ë\÷.\ÄJ¡•uY£\É\ñª/','../Uploads/storage/4077cd67-f70f-4d04-86c8-3f6337c558d6-1.cloud','active',NULL),('ç¬¬2æ¬¡å®è®­è¯¾ ç®€å†åˆ¶ä½œ æœ€ç»ˆæ•ˆæœ.docx','2026-07-05 11:01:34',478844,'application/vnd.openxmlformats-officedocument.wordprocessingml.document',_binary 'UUUUUUUUUUUUUUUU',_binary '(\õ3Ui\ÛGï°¬\Íı4F','07261043b59ecdbf31023b264f932a5257968e047355228cd7985350346a04e2',1,_binary '×’Aİ»\ñ@‰ic±&2N','../Uploads/storage/49b4ad14-401e-486c-ac43-65a2b0c9f8e1-1.cloud','active',NULL),('url.jar','2026-07-13 15:37:16',4529,'application/java-archive',_binary 'UUUUUUUUUUUUUUUU',_binary ')\ØÜ§\Ä\ÈDK½\æ\æMR}','ce5a83aee31153cca30274ac94467b316edea8cb28acf72f52f5a72d455b1b43',1,_binary '\ã•°Ö¢O\óƒ\\F._.','../Uploads/storage/f1701bce-7a9b-4650-8e07-7b55e39f8d6a-1.cloud','active',NULL),('RegisterVerificationSendRequest.md','2026-07-13 11:19:26',875,'text/markdown',_binary 'UUUUUUUUUUUUUUUU',_binary '*›ş¸9\òKb­\âP \áy\İ','eeda1ee3c6eabb18eea93ec3271f064d470ba5a25835a8ec7e7e90e316a8c0db',1,_binary 'P\åqgbL\n¡W\Ù77²¬','../Uploads/storage/ec6c6d0e-5aaf-40a8-b373-6cdd36317458-1.cloud','active',NULL),('clion.vmoptions','2026-07-05 14:08:52',702,'application/octet-stream',_binary 'UUUUUUUUUUUUUUUU',_binary '*»Áˆ‰»A<kpdø?','84a349d678b98359cc6fb13dd63c0cf3b790bce13f6f2b2284726b70386b13f6',1,_binary '­\\Œ\ñ;N\á\É$TĞ \Z','../Uploads/storage/ee79633a-eacd-49e8-8c10-c5ac6f1a5106-1.cloud','active',NULL),('hideme.jar','2026-07-13 15:37:14',7209,'application/java-archive',_binary 'UUUUUUUUUUUUUUUU',_binary '+®a\ôûMAl¾›Jm\õH‡','fa14c735ab9fed3f3a5df0dc78a5d38ae0a146099ddc858197e9f528bd996c40',1,_binary '\ã•°Ö¢O\óƒ\\F._.','../Uploads/storage/53f70c89-24ff-4279-b8f9-578278ebab6f-1.cloud','active',NULL),('gateway.vmoptions','2026-07-13 15:36:50',634,'application/octet-stream',_binary 'UUUUUUUUUUUUUUUU',_binary ',QŒ\ô\ñE’Tœ\Ë\ödK','c03bf5ae617eae087cf21a339dd956ce1260d11863e60a21b8b0365f4afdc7d5',1,_binary '‰º\Ë\÷.\ÄJ¡•uY£\É\ñª/','../Uploads/storage/132b3a83-60ec-4921-b29f-46f90d17cf3a-1.cloud','active',NULL),('6cd690322d1cc081fd4e4b75ec1691e8.jpeg','2026-07-05 10:36:36',76552,'image/jpeg',_binary 'UUUUUUUUUUUUUUUU',_binary '-ß±…\ğBü·³\Ş\Zaª.','b478e76050f93c219b0e64b0e0cfb4b2dcc4872f0d52dc5305f56c1974381752',1,_binary 'ù0¢\\@`¤\Ã\nœ\ë\"','../Uploads/storage/1a7b6e6b-1a43-4153-87e9-dc3c49fb69b6-1.cloud','active',NULL),('gclient','2026-07-05 09:45:59',322,'application/octet-stream',_binary 'UUUUUUUUUUUUUUUU',_binary '.@¬I­I´¥\ÌG;Fd‰\Ã','c26028908bafe0cdfc578aa3d54a45dcea71adb9f28e081783ca557138cc1998',1,_binary 'ªªªªªªªªªªªªªªª¥','../Uploads/storage/e3824bf6-49ee-45e5-8c87-0f0e8f2f5fa2-1.cloud','active',NULL),('idea.vmoptions','2026-07-13 15:36:34',635,'application/octet-stream',_binary 'UUUUUUUUUUUUUUUU',_binary '.šQ>euLÎŒ\ñ\ïW\İ','58b9de329a0a3786ca2b531abb2b66e91e78e475458faa284133b8fb92938cee',1,_binary '‰º\Ë\÷.\ÄJ¡•uY£\É\ñª/','../Uploads/storage/b4bc3afc-ae53-497b-bb0e-8a8ea78bdad4-1.cloud','active',NULL),('uninstall-all-users.vbs','2026-07-05 14:08:39',1065,'application/octet-stream',_binary 'UUUUUUUUUUUUUUUU',_binary '.È€;Bb®mIú´@ú','11811f0c25f30336a0c835dad7e30e7c9810392d207540c847da0e1b7c06ce72',1,_binary 'ÅˆĞ£3\ÎL1›\İ\á\rK\á©\ò','../Uploads/storage/f91d0de5-d3cf-4dfb-8f09-0c6772eb52f0-1.cloud','active',NULL),('FileStarControllerApi.md','2026-07-13 11:21:43',13561,'text/markdown',_binary 'UUUUUUUUUUUUUUUU',_binary '/i$)\ìƒN™¬ù\ß#­U³','5cce7f1c0e688cadffb0ea8a32ba8d7f1036949614ef3a7a43d2f96fe8821bb7',1,_binary 'P\åqgbL\n¡W\Ù77²¬','../Uploads/storage/a5d63f7c-943d-404a-9dba-02e56ad3f0be-1.cloud','active',NULL),('url.jar','2026-07-05 14:09:13',4529,'application/java-archive',_binary 'UUUUUUUUUUUUUUUU',_binary '/Ÿ6\âCÁ§¡eˆ\0ˆ','ce5a83aee31153cca30274ac94467b316edea8cb28acf72f52f5a72d455b1b43',1,_binary 'N‡4LŒ DU»A—YGZ','../Uploads/storage/e0db74b5-7244-468b-9f80-68e0982d33db-1.cloud','active',NULL),('CarTests.py','2026-07-07 11:12:12',171,'text/x-python-script',_binary 'UUUUUUUUUUUUUUUU',_binary '4i„¹ùBú¯\Æc5¿7\Õ','638252a84582310fbdf23a3035958e46fe55b5d0f6e406dc7c8fe32fa96a56c2',1,_binary 't…¯»eµK\"¨\ÍZ›‚•¬','../Uploads/storage/3416c82c-4cd8-4ac9-8098-cd3f4e004b7e-1.cloud','active',NULL),('UploadsControllerApi.md','2026-07-13 11:21:52',3045,'text/markdown',_binary 'UUUUUUUUUUUUUUUU',_binary '4\é³\à\×IW¯nªL1\r\ç	','f7269782967f12aa8591d3e4ea2784dd10edc0b9a2e652a18c6b3856c4b6153f',1,_binary 'P\åqgbL\n¡W\Ù77²¬','../Uploads/storage/8c05a2ed-f6ef-4083-ba84-d1c61776b1c1-1.cloud','active',NULL),('VerificationSendVO.md','2026-07-13 11:21:49',689,'text/markdown',_binary 'UUUUUUUUUUUUUUUU',_binary '5™€bLy“¼\õ\Ü\ô','34455192b8f7a127cfe061a8117992212c6b9b0172596cf6f6a3aa82a191c712',1,_binary 'P\åqgbL\n¡W\Ù77²¬','../Uploads/storage/0ea75741-2440-4942-8156-58b09077b9b2-1.cloud','active',NULL),('1.py','2026-07-07 11:12:49',2096,'text/x-python-script',_binary 'UUUUUUUUUUUUUUUU',_binary '5¸E\Ö\ÏeC®„\Æp­\Ó5Çˆ','444f9f48baaa0605205dc2b2dbdfd945fb06d705711a851502657a4cf00852b2',1,_binary 'ªªªªªªªªªªªªªªª¥','../Uploads/storage/92fd3604-5963-45ec-9e9c-4b9fbd73ef30-1.cloud','active',NULL),('å¤§å­¦è‹±è¯­Aï¼ˆ1ï¼‰æ•™é™¢.xls','2026-07-06 10:39:27',32256,'application/vnd.ms-excel',_binary 'UUUUUUUUUUUUUUUU',_binary '8|\ñ\Ú%N\ö´i\àRTªTp','5cd9862aa7b08e7438db9f18f00e5948debd408fdf7e920bbab2dac674b26bf7',1,_binary '\á\ášlCÌ©\ô\\\óU2²','../Uploads/storage/e1b2ff3c-2f66-41d7-a32f-ac5993d0c861-1.cloud','active',NULL),('å¤§å­¦è‹±è¯­B(1) ç­¾åˆ°è¡¨ï¼ˆç”ŸåŒ–ï¼‰.xls','2026-07-06 10:39:13',83968,'application/vnd.ms-excel',_binary 'UUUUUUUUUUUUUUUU',_binary ':\Êıë”‡A“\Ó\ÖÇœ(','3afeeff4629c625868825ff9e895ea9f89732e80b458e7fd0b69f7b29caa9439',1,_binary '\Z\ëd\ÕR\ZA¿³U½Ö­‹¢','../Uploads/storage/e64a0649-4bd0-4deb-9160-85702e8df278-1.cloud','active',NULL),('ä¸‰ç§æ–¹å¼é€‰æ‹©å…¶ä¸­ä¸€ç§å³å¯.txt','2026-07-13 15:35:12',0,'text/plain',_binary 'UUUUUUUUUUUUUUUU',_binary '=\ğe¼³A9¨r¬Î“>£','e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855',1,_binary 'ªªªªªªªªªªªªªªª¥','../Uploads/storage/509fd2dd-dff6-41db-a6ed-4cd78daf8db6-1.cloud','active',NULL),('è€ƒåœºè€ƒè¯•å®‰æ’ï¼ˆè€ƒç”Ÿç­¾åˆ°è¡¨ï¼‰æ€æƒ³é“å¾·ä¸æ³•æ²».xls','2026-07-06 10:39:36',240128,'application/vnd.ms-excel',_binary 'UUUUUUUUUUUUUUUU',_binary '=a\n0uGÛŒ[·\×^X\É','5dad49c0deea439dd4637ef33efa66ff4c29ee0d411c9c6b8cec72ba91304262',1,_binary '³¢\ÂAx\ôAG\öˆœ','../Uploads/storage/4d5d4b05-08fa-478a-97f1-499634151ce9-1.cloud','active',NULL),('FileEntity.md','2026-07-13 11:20:10',1255,'text/markdown',_binary 'UUUUUUUUUUUUUUUU',_binary '=øm¼#LMŠI©R\Õ\Õ','a9e8cea527c729c0742240a2f2c742ddd324c989ea5548c43b8ff4fd5beca376',1,_binary 'P\åqgbL\n¡W\Ù77²¬','../Uploads/storage/d63a95ba-d5be-404b-a599-fc0524a257f5-1.cloud','active',NULL),('car.py','2026-07-07 11:12:44',886,'text/x-python-script',_binary 'UUUUUUUUUUUUUUUU',_binary '?hE§q»GÊ£°\Öı\0V·','2aa53214238120d6a91369b1a8127c0af34fb4af42fb310c5eda5fcafa575ad9',1,_binary 'ªªªªªªªªªªªªªªª¥','../Uploads/storage/64504f37-7a6f-41b6-b832-7ff15de5ddb5-1.cloud','active',NULL),('phpstorm.vmoptions','2026-07-13 15:37:06',635,'application/octet-stream',_binary 'UUUUUUUUUUUUUUUU',_binary 'F‹W›dI@¦)\ØH«Zg','58b9de329a0a3786ca2b531abb2b66e91e78e475458faa284133b8fb92938cee',1,_binary '‰º\Ë\÷.\ÄJ¡•uY£\É\ñª/','../Uploads/storage/0fe186a2-dc90-4cd6-bae9-e1fa96aca421-1.cloud','active',NULL),('controlconfig.conf','2026-07-05 09:54:35',1331,'application/octet-stream',_binary 'UUUUUUUUUUUUUUUU',_binary 'F\çk\ñªZFH±oªF;…‰','e07a20bbaee529ed4b0bcbd973e4656ce7f11dc6037ff318fbde3f25d207eea0',1,_binary 'ªªªªªªªªªªªªªªª¥','../Uploads/storage/4b69844e-0673-4e6d-bcde-1db08e782537-1.cloud','active',NULL),('ShareAccessInfoVO.md','2026-07-13 11:21:41',1446,'text/markdown',_binary 'UUUUUUUUUUUUUUUU',_binary 'H3½´\İDŒ¥\n\ñ£,€²','5c737722b616bf26a36f77e8923320322e606fac42f60b074ae95d4fe9391a55',1,_binary 'P\åqgbL\n¡W\Ù77²¬','../Uploads/storage/155efca2-ed65-4c58-9c10-67eaa5d14e10-1.cloud','active',NULL),('JsonResultListShareContentItemVO.md','2026-07-13 11:20:27',739,'text/markdown',_binary 'UUUUUUUUUUUUUUUU',_binary 'Ho\Î\äıDøƒ 7\×Yj0¼','463eec2c48b2bf6e0913995db3e872fd81d7e37e336311e832880fe2cc36d902',1,_binary 'P\åqgbL\n¡W\Ù77²¬','../Uploads/storage/5c4077bf-a503-4d1f-a31d-655e28bf303c-1.cloud','active',NULL),('NS4150Cè§„æ ¼ä¹¦.zip','2026-07-08 19:28:02',3898238,'application/zip',_binary 'UUUUUUUUUUUUUUUU',_binary 'JÍ¥%n˜@Áµ+/Ò¾*','bc56f9bb35fd7bd866a38b4ef6827bd60185d1ad0323cb31df69e52ded3afcec',1,_binary 'N‡4LŒ DU»A—YGZ','../Uploads/storage/ee13fa7d-e32b-4ebf-a4a5-0c9b3184a63f-1.cloud','active',NULL),('ja-netfilter.jar','2026-07-13 15:35:20',48642,'application/java-archive',_binary 'UUUUUUUUUUUUUUUU',_binary 'O;k\×\æDœ±\ÖG§¾WT','7f91af6beac337409247b7a93e1a10ec6003964b3214acdcc38afbe142f236d2',1,_binary 'Vyƒ¢IK·§X‚‰V^','../Uploads/storage/f34b46fe-286f-4b09-97b4-17356e6de040-1.cloud','active',NULL),('java_error_in_studio.hprof','2026-06-14 08:59:04',383744933,'application/octet-stream',_binary 'UUUUUUUUUUUUUUUU',_binary 'P–\Í\Ö\ã*@\0¾®t\r\ä\Üy','6b47537f8e733eb2bacd880a43c378216bf9753e699dae150de12ce27d9eea0f',74,_binary 'ªªªªªªªªªªªªªªª¥',NULL,'merging',NULL),('è“ç‰™éŸ³é¢‘ç¤ºä¾‹æºç _Arduinoc.zip','2026-07-08 19:15:22',101111,'application/zip',_binary 'UUUUUUUUUUUUUUUU',_binary 'Qd\ñÁ™IƒNÚº\÷wV','c61764a46ca294f05fe7942bba6c97528ac8658c1335a87aff26916c38618050',1,_binary 'ªªªªªªªªªªªªªªª¥','../Uploads/storage/47947c1b-3e46-4dfb-aa6d-db388afd6b6f-1.cloud','active',NULL),('dns.jar','2026-07-05 14:09:07',4859,'application/java-archive',_binary 'UUUUUUUUUUUUUUUU',_binary 'Q\à\'9JeŸ\Ê¢¬ş','d1150b1831b112b93d74a34a10ce6c11606e0d2255d532c29f91f1d92b40a552',1,_binary 'N‡4LŒ DU»A—YGZ','../Uploads/storage/a2c1cf3e-60e2-4170-b03c-665f00d9e728-1.cloud','active',NULL),('ShareLinkEntity.md','2026-07-13 11:21:36',2024,'text/markdown',_binary 'UUUUUUUUUUUUUUUU',_binary 'R•\ö»~`O”‹J\Ï\à\'a','95ef64f970c36471c8c598f5d97a317932b739fad4d216367db90e9ed90d2abd',1,_binary 'P\åqgbL\n¡W\Ù77²¬','../Uploads/storage/1f16e832-dd08-439f-aa25-4b86a92b9b99-1.cloud','active',NULL),('.DS_Store','2026-07-13 15:35:48',10244,'application/octet-stream',_binary 'UUUUUUUUUUUUUUUU',_binary 'TR\×h™J#­Ñ€6Z0	','5612006f44a45f03014ceb262831ac51af1fec29279812c04de319e00f437011',1,_binary '‚1_I¶ˆ!rHt\ë”','../Uploads/storage/aea283f8-55d1-4c8a-b7b5-37b8e031af9e-1.cloud','active',NULL),('UploadsChunkInternalVO.md','2026-07-13 11:21:29',895,'text/markdown',_binary 'UUUUUUUUUUUUUUUU',_binary 'TÜ\0-†Iw¾øGM‰ß¤','c41b41c9b7d784b437c4b3ff18dbde16a57a590c24af160d879259a74cefa02b',1,_binary 'P\åqgbL\n¡W\Ù77²¬','../Uploads/storage/a7ee7d3d-4494-478a-9e9c-40b379401928-1.cloud','active',NULL),('url.jar','2026-07-13 15:35:33',4529,'application/java-archive',_binary 'UUUUUUUUUUUUUUUU',_binary 'U$¶¬<\ÈGÀ¯Å£¼S:\Ò','ce5a83aee31153cca30274ac94467b316edea8cb28acf72f52f5a72d455b1b43',1,_binary '\ËYú˜~@â‘®]%\Ş','../Uploads/storage/ef9123c5-0cdc-4f45-a071-25de9d24b923-1.cloud','active',NULL),('webide.vmoptions','2026-07-13 15:36:48',635,'application/octet-stream',_binary 'UUUUUUUUUUUUUUUU',_binary 'Uf_a·\ÄH6‘\Ë\Ü\Üm”\0','58b9de329a0a3786ca2b531abb2b66e91e78e475458faa284133b8fb92938cee',1,_binary '‰º\Ë\÷.\ÄJ¡•uY£\É\ñª/','../Uploads/storage/c1fde6ec-3dd6-4fff-85e3-ba349927c897-1.cloud','active',NULL),('pycharm.vmoptions','2026-07-05 14:08:56',702,'application/octet-stream',_binary 'UUUUUUUUUUUUUUUU',_binary 'ZşX­J\ñ†š\ÕE\×~€','84a349d678b98359cc6fb13dd63c0cf3b790bce13f6f2b2284726b70386b13f6',1,_binary '­\\Œ\ñ;N\á\É$TĞ \Z','../Uploads/storage/a66a7de0-7a77-4576-8469-7a61b66aa26d-1.cloud','active',NULL),('JsonResultInteger.md','2026-07-13 11:20:47',644,'text/markdown',_binary 'UUUUUUUUUUUUUUUU',_binary '\\]\ô;ÇB\áš[\Â`²¢r','15bc62b33b10da195613f90840ac2a4373a76f1ba18ee78677b4d3f3e0957f0f',1,_binary 'P\åqgbL\n¡W\Ù77²¬','../Uploads/storage/20008739-6c55-4780-b27b-313e3a86a8c1-1.cloud','active',NULL),('FileStarVO.md','2026-07-13 11:20:05',1066,'text/markdown',_binary 'UUUUUUUUUUUUUUUU',_binary '^>[D™I“#dU8\İ\Ü','16ab90b4ae39de2a0d23d5e5e2d608aa96b22fa7672165d4a88b36dbbb7abee7',1,_binary 'P\åqgbL\n¡W\Ù77²¬','../Uploads/storage/ecfcb91e-feb6-4480-8cc4-cbf4ac89b512-1.cloud','active',NULL),('å¤§å­¦è‹±è¯­Bï¼ˆ1ï¼‰å¤§æ•°æ®ç­¾åˆ°è¡¨ .xls','2026-07-06 10:39:11',89600,'application/vnd.ms-excel',_binary 'UUUUUUUUUUUUUUUU',_binary '^i\ä{eEş8†€¡','a76bf07ed8751147c705387b5781bed84531d3acce2d35a28540bbfe4d37c7a0',1,_binary '\Z\ëd\ÕR\ZA¿³U½Ö­‹¢','../Uploads/storage/9e80c239-4695-4349-8da4-0100ac3d4cbe-1.cloud','active',NULL),('readme.txt','2026-07-13 15:35:22',504,'text/plain',_binary 'UUUUUUUUUUUUUUUU',_binary '`\0\ôÿ#WE:‹\ĞqŠ\"\ÉT±','fb0a4649ae65aec46fc74d83effbcf51f535e82e80b9063497436165007199bf',1,_binary 'Vyƒ¢IK·§X‚‰V^','../Uploads/storage/8b70fa54-c478-4169-b628-6c703d2359f9-1.cloud','active',NULL),('RegisterUserRequest.md','2026-07-13 11:20:25',865,'text/markdown',_binary 'UUUUUUUUUUUUUUUU',_binary '`}´{„\ğE€»\æ>`\êü*','edcac5c41166025275259fbac8c3e861158e0c2fd73214f835cdfe63a28a4486',1,_binary 'P\åqgbL\n¡W\Ù77²¬','../Uploads/storage/3fb38f87-df99-4cf0-b65b-b6346cccc1c7-1.cloud','active',NULL),('LoginRequest.md','2026-07-13 11:20:49',822,'text/markdown',_binary 'UUUUUUUUUUUUUUUU',_binary '`\Ô\Ö:=\êCŒ§D^\İ\åÿy','2682cb8921498baf407f62d361e216d6350e6fd1492e72b418d22c5f5c4e2fe9',1,_binary 'P\åqgbL\n¡W\Ù77²¬','../Uploads/storage/72a4108a-ee3d-403c-bb2c-206a6c304e8f-1.cloud','active',NULL),('PageResultVONodeVO.md','2026-07-13 11:20:36',833,'text/markdown',_binary 'UUUUUUUUUUUUUUUU',_binary 'a#/w£/Jµ™Ÿ_7vs9','270dba6309a0296ff50d5501cf4f98a258069f0dde45e23870b8b10de8a8f0db',1,_binary 'P\åqgbL\n¡W\Ù77²¬','../Uploads/storage/155bf11f-3b6f-454b-9f2d-ea4122f7fab0-1.cloud','active',NULL),('é™„ä»¶2ï¼š2025å¹´ç§‹å­£å­¦æœŸæœŸæœ«è€ƒè¯•å®‰æ’è¡¨ï¼ˆæ•°å­¦ä¸å¤§æ•°æ®å­¦é™¢ï¼‰ - - é«˜ç­‰æ•°å­¦A1.xlsx','2026-07-06 10:39:44',66583,'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',_binary 'UUUUUUUUUUUUUUUU',_binary 'a‘&HNo½\æ©R\ÇÙ¤','bd2c22d608c63a704006fc3e9794ed74dd58746ffcc8b076972c9dc51deee456',1,_binary '\nh¯H\ÚMĞŸ·3Û >«','../Uploads/storage/101b2761-da7b-43a8-b13c-57c002c7d817-1.cloud','active',NULL),('appcode.vmoptions','2026-07-13 15:36:24',635,'application/octet-stream',_binary 'UUUUUUUUUUUUUUUU',_binary 'b![^O|¨úh½\ÇCa','58b9de329a0a3786ca2b531abb2b66e91e78e475458faa284133b8fb92938cee',1,_binary '‰º\Ë\÷.\ÄJ¡•uY£\É\ñª/','../Uploads/storage/74ebf539-01f7-4e99-8667-80bd4fdd1060-1.cloud','active',NULL),('å­¦å‰æ•™è‚²å­¦é™¢ å¤§å­¦è‹±è¯­Bï¼ˆ1ï¼‰.xls','2026-07-06 10:39:18',78336,'application/vnd.ms-excel',_binary 'UUUUUUUUUUUUUUUU',_binary 'c…q–\ÈBR\ô\òÀ¸\ï','9ac4d87150ae7b81787fd601389b6d9ee19df084bf0716ed74031130bc3bc59a',1,_binary '\Z\ëd\ÕR\ZA¿³U½Ö­‹¢','../Uploads/storage/6694ad4e-0a36-4fd2-9a9a-5d3ebf63f2ab-1.cloud','active',NULL),('å¤§å­¦è‹±è¯­Aï¼ˆ1ï¼‰ä½“é™¢.xls','2026-07-06 10:39:29',55296,'application/vnd.ms-excel',_binary 'UUUUUUUUUUUUUUUU',_binary 'd«\Ô\Z¯[N›0´ƒ…','98218f27e9467c2670e0788e220e98abed0324f127c8cef29d2a4f6863947531',1,_binary '\á\ášlCÌ©\ô\\\óU2²','../Uploads/storage/2939fdfe-1b7f-4337-bb67-45ba8c1a0c8f-1.cloud','active',NULL),('clion.vmoptions','2026-07-13 15:36:21',635,'application/octet-stream',_binary 'UUUUUUUUUUUUUUUU',_binary 'e\áRp¬Fpº	$À\ñ±:\ï','58b9de329a0a3786ca2b531abb2b66e91e78e475458faa284133b8fb92938cee',1,_binary '‰º\Ë\÷.\ÄJ¡•uY£\É\ñª/','../Uploads/storage/5888b523-151f-4f60-b8bc-2e8d1541df22-1.cloud','active',NULL),('è‹±è¯­I.xls','2026-07-06 10:38:54',33792,'application/vnd.ms-excel',_binary 'UUUUUUUUUUUUUUUU',_binary 'f³H\Z\İGr´\Í¥\\\íM\Ì','4b07a27e953865ba1ec77c0cd5f1a09133e22e81b3ca095ff26ee6346774855e',1,_binary '\Z($\ò¦\rO0ƒ\ï\â\0\ó\Í\ì','../Uploads/storage/48f25011-be69-40c5-a892-319696aa144d-1.cloud','active',NULL),('png3.png','2026-07-05 10:03:00',49508,'image/png',_binary 'UUUUUUUUUUUUUUUU',_binary 'h(6[ \óMÂ¬J¸¾H\Z\ö','104665a95c02a76755ad284e2b4510f36f0907e2c6cc052319517d4893b9af69',1,_binary 'ù0¢\\@`¤\Ã\nœ\ë\"','../Uploads/storage/eec45166-9dc0-49cd-8f6c-16bdf8c11fcf-1.cloud','active',NULL),('VERSION','2026-07-13 11:19:11',7,'application/octet-stream',_binary 'UUUUUUUUUUUUUUUU',_binary 'hPr¡£A5€‰¦*%\ì\éu','9cc9b8f746172545b7fef01d780b20ec4df246052a570939fb9d0fb9a1689ccf',1,_binary 'ò•†ŠV\ÆOÙ†]øs¢4','../Uploads/storage/2db04967-45b1-4a3a-aeec-5798c87d94e3-1.cloud','active',NULL),('png2.png','2026-07-05 10:02:57',291829,'image/png',_binary 'UUUUUUUUUUUUUUUU',_binary 'jƒ\ñ>rÁ@¥µÓ°\ğkj\ŞK','2b29a1ce8e00c60427b14bb7f1ca73831c2fc9ced9b1ffde576c68a9031df4ba',1,_binary 'ù0¢\\@`¤\Ã\nœ\ë\"','../Uploads/storage/bc007630-ab6b-41e1-9f38-5c497801d7e7-1.cloud','active',NULL),('CarWebRTCModel.py','2026-07-07 11:12:14',5768,'text/x-python-script',_binary 'UUUUUUUUUUUUUUUU',_binary 'j¢\â\'EJŸ¤œ\\\îVG','5d64d1c53089aa0bb81fbed8f7225ac1970137e3f72bb944f98dac2a612a3d0a',1,_binary 't…¯»eµK\"¨\ÍZ›‚•¬','../Uploads/storage/ef0aad65-aff2-418e-92ec-a95127274d35-1.cloud','active',NULL),('InternalFileMetadataVO.md','2026-07-13 11:20:03',1216,'text/markdown',_binary 'UUUUUUUUUUUUUUUU',_binary 'k@ ª\é´A \Ğ8©*R\×','bc1adcf56a846dad0c875da0421e8037f81112e96b137e61eb95fffd2e636e46',1,_binary 'P\åqgbL\n¡W\Ù77²¬','../Uploads/storage/0dbffbb1-0762-4654-b631-636ac3116409-1.cloud','active',NULL),('CarConfig.cpython-310.pyc','2026-07-07 11:12:26',485,'application/octet-stream',_binary 'UUUUUUUUUUUUUUUU',_binary 'kR¿™‹QOş¤›-ü\õ\"²\î','702193bce5d0e45ca04dda820d2bf2c44d00435cd07ddc102b0c8dbcef8f0b28',1,_binary 'Øƒ\ÒY\r¼JS¼°\0`¯$','../Uploads/storage/d88116e5-b6f0-4ab8-9995-3f39154b5272-1.cloud','active',NULL),('JsonResultUploadsSessionInternalVO.md','2026-07-13 11:19:28',744,'text/markdown',_binary 'UUUUUUUUUUUUUUUU',_binary 'k\Ù@‚NE+“¨¶µ‹P´','cfa44ce35b7a2c80a1fc5d0f18f280c9260a6c2897c7261cb89cb25939dbb74d',1,_binary 'P\åqgbL\n¡W\Ù77²¬','../Uploads/storage/25c62152-9266-4e6e-b586-ecc42b94e6a2-1.cloud','active',NULL),('uninstall.sh','2026-07-13 15:36:07',1805,'text/x-sh',_binary 'UUUUUUUUUUUUUUUU',_binary 'l\ì5\ÂYK=¸Œ‰\îR','ec93dfcdf02f00f21bff552e3ee6899850877a8cc7dd08033d474050ac67a956',1,_binary 'iJr\áD¼²‰¥\Ë\ãW²','../Uploads/storage/b282df76-1ab3-4eba-bf27-051e75ef2683-1.cloud','active',NULL),('CarBatteryModel.py','2026-07-07 11:12:37',916,'text/x-python-script',_binary 'UUUUUUUUUUUUUUUU',_binary 'ni-D“A>¨\î_\Ó.{…','ca7508273a49784061d3ed200a5693843f5c28bee7b4f3a3f22e3271c90fe627',1,_binary 't…¯»eµK\"¨\ÍZ›‚•¬','../Uploads/storage/5d55f96d-3950-422d-9928-f78652b308bb-1.cloud','active',NULL),('é™„ä»¶2ï¼š2025å¹´ç§‹å­£å­¦æœŸæœŸæœ«è€ƒè¯•å®‰æ’è¡¨ï¼ˆæ•°å­¦ä¸å¤§æ•°æ®å­¦é™¢ï¼‰ - - æ¦‚ç‡è®ºä¸æ•°ç†ç»Ÿè®¡A.xlsx','2026-07-06 10:39:49',69689,'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',_binary 'UUUUUUUUUUUUUUUU',_binary 'o‚qŠ\ĞD’\ÉØ›)','6c40a22620e67b8ea453fdacfac99a223b2cf762a78074b5b21c086b9a301f5a',1,_binary '\nh¯H\ÚMĞŸ·3Û >«','../Uploads/storage/8192837b-4c63-49d0-b739-3f36c5b4583b-1.cloud','active',NULL),('2.py','2026-07-07 11:12:40',2301,'text/x-python-script',_binary 'UUUUUUUUUUUUUUUU',_binary 'oÊ»z\ÜEÏ¾¿‹\n5Ù°','ce5621b3f888fd74273e3980d750fbaf337d26066a903444e9148e97b0c5f71e',1,_binary 'ªªªªªªªªªªªªªªª¥','../Uploads/storage/8a6a3ca3-98e4-4e89-bd4d-49cbe0de1e1b-1.cloud','active',NULL),('hideme.jar','2026-07-13 15:35:30',7209,'application/java-archive',_binary 'UUUUUUUUUUUUUUUU',_binary 'p\Ï5ŸH “\÷\Ê|\Õ¨','fa14c735ab9fed3f3a5df0dc78a5d38ae0a146099ddc858197e9f528bd996c40',1,_binary '\ËYú˜~@â‘®]%\Ş','../Uploads/storage/9b0b4a4b-3b0d-4998-b709-76206813d1c1-1.cloud','active',NULL),('JavaäºŒçº§å¤§çº².pdf','2026-06-13 19:38:18',120547,'application/pdf',_binary 'UUUUUUUUUUUUUUUU',_binary 'r	/ }K‹§’­;©','3ea82b01db7948e60f471ae6ac23beb6ea659b69077a35a91689c7dd306606d3',1,_binary 'ªªªªªªªªªªªªªªª¥','../Uploads/storage/0b71e23c-e14b-4ee8-93d7-c218fbb73be1-1.cloud','deleted',NULL),('JsonResultInternalFileMetadataVO.md','2026-07-13 11:22:01',734,'text/markdown',_binary 'UUUUUUUUUUUUUUUU',_binary 'rpÌ¨M-@&³E	\ì','a92ca158760d61a5479742ac12db73164dbfdb6f6a2681adbcffcfb722cf824e',1,_binary 'P\åqgbL\n¡W\Ù77²¬','../Uploads/storage/e7176fc2-5277-49fe-9ece-fd2f7586afe0-1.cloud','active',NULL),('tests7.py','2026-06-13 19:05:22',4638,'text/x-python-script',_binary 'UUUUUUUUUUUUUUUU',_binary 't‡=Ï\ÌIÇ¯¾;c\Õg','aeab8ac72fbe88e484ab1a921352fb0cf6b074ee57b5b5826fe3254daf1e9c13',1,_binary 'ªªªªªªªªªªªªªªª¥','../Uploads/storage/da9f4f0d-4837-4075-886c-247253dca1c6-1.cloud','deleted',NULL),('api-docs.yaml','2026-07-05 10:57:26',91487,'application/x-yaml',_binary 'UUUUUUUUUUUUUUUU',_binary 'tµ ¬b\ô@}qh€„9tG','d9076faa950c3251dc3aa7cb3de71c6dc00c03f545aa5f343a25cbbd5d925b7f',1,_binary 'ªªªªªªªªªªªªªªª¥','../Uploads/storage/b1d46eb8-bb0d-4e96-86dd-d630447a9c6a-1.cloud','active',NULL),('UpdateUserInfoRequest.md','2026-07-13 11:21:21',659,'text/markdown',_binary 'UUUUUUUUUUUUUUUU',_binary 'u6ú/NP›\Ê\Ë2pnßŒ','666016564d42691d31ce5c1fe1f818513ee67df5d515c53d5020278283026d11',1,_binary 'P\åqgbL\n¡W\Ù77²¬','../Uploads/storage/2fa8f6f9-cfd4-46a5-85dd-2d0966685a63-1.cloud','active',NULL),('ShareLinkVO.md','2026-07-13 11:20:00',1555,'text/markdown',_binary 'UUUUUUUUUUUUUUUU',_binary 'x;ş\å\ë=E›¥|ƒH»˜A','8b602be342f6caffb6cb9b3f84888b9db60d4b9586a9d1e74daec3cb85f11774',1,_binary 'P\åqgbL\n¡W\Ù77²¬','../Uploads/storage/0844aba5-ae1b-4bea-a170-bb3e036bb94c-1.cloud','active',NULL),('cqooc.py','2026-07-05 10:02:46',5841,'text/x-python-script',_binary 'UUUUUUUUUUUUUUUU',_binary 'x˜f\Ü\Å4O%´\Ğ\æ\Ü\çb\Ô\æ','33df5b24ecc343ec4754f023eb245e43120093524c2fdbf67b5807215e8ebfdf',1,_binary 'ªªªªªªªªªªªªªªª¥','../Uploads/storage/29e3a57e-9627-48c7-a227-5c95d7990e52-1.cloud','active',NULL),('uninstall.sh','2026-07-05 14:08:41',1805,'text/x-sh',_binary 'UUUUUUUUUUUUUUUU',_binary 'z=)y\Ã\ÒE-—\ò[Çf\õH','ec93dfcdf02f00f21bff552e3ee6899850877a8cc7dd08033d474050ac67a956',1,_binary 'ÅˆĞ£3\ÎL1›\İ\á\rK\á©\ò','../Uploads/storage/f506a30b-0b97-46df-814c-5f5848d659c3-1.cloud','active',NULL),('JsonResultListTrashTargetVO.md','2026-07-13 11:20:51',714,'text/markdown',_binary 'UUUUUUUUUUUUUUUU',_binary '{g%§F†Œ9\å’P4\ğ','977197e58ae325b6b9a492d3190039e02327e6626f8c1af1fcc8dd5b013c0571',1,_binary 'P\åqgbL\n¡W\Ù77²¬','../Uploads/storage/53d2b8e5-8e9b-4aad-aac4-a6658af11215-1.cloud','active',NULL),('rider.vmoptions','2026-07-13 15:37:03',635,'application/octet-stream',_binary 'UUUUUUUUUUUUUUUU',_binary '|\Öq‡\ÚGË²’>²RŸn','58b9de329a0a3786ca2b531abb2b66e91e78e475458faa284133b8fb92938cee',1,_binary '‰º\Ë\÷.\ÄJ¡•uY£\É\ñª/','../Uploads/storage/5091fc66-66ae-4adc-a7c5-8a2296a0e231-1.cloud','active',NULL),('ç­¾åˆ°è¡¨-å¤§å­¦è‹±è¯­Bï¼ˆ1ï¼‰æ•™é™¢.xls','2026-07-06 10:39:06',70656,'application/vnd.ms-excel',_binary 'UUUUUUUUUUUUUUUU',_binary 'û,\õ9Hv§Ÿ©Û»\ë¡\à','9bab57c1270bdba1681b5235fc62924e5f15a22b41710c59574818c17e1fa704',1,_binary '\Z\ëd\ÕR\ZA¿³U½Ö­‹¢','../Uploads/storage/a51aab3c-da1e-4985-864e-d5729adb0165-1.cloud','active',NULL),('JsonResultUploadsChunkInternalVO.md','2026-07-13 11:19:19',734,'text/markdown',_binary 'UUUUUUUUUUUUUUUU',_binary '€<+¿@¹\\Q¹\ô	O','4627022272be7e78c83be055907b6e62351591990961e17b587b7e4c453ca6a7',1,_binary 'P\åqgbL\n¡W\Ù77²¬','../Uploads/storage/84b2c87f-b65b-454e-b2d4-0295ea073b2b-1.cloud','active',NULL),('JsonResultPageResultVONodeVO.md','2026-07-13 11:20:58',714,'text/markdown',_binary 'UUUUUUUUUUUUUUUU',_binary '9U>ú·K”¿c¼À„h','394305d07b07412f09b3018785a51662214e1b77c451961b2018594a0cb58d6e',1,_binary 'P\åqgbL\n¡W\Ù77²¬','../Uploads/storage/e888ac96-9119-4423-9a69-5f748c28bffd-1.cloud','active',NULL),('crack.sh','2026-07-06 11:38:45',668,'text/x-sh',_binary 'UUUUUUUUUUUUUUUU',_binary 'ƒw\Õ#OSNg©m¢!©aº','bf9fd7b27435de7c2cad0a64c65903b922106fb6e0f5a98fe61b622d77c921a8',1,_binary 'ªªªªªªªªªªªªªªª¥','../Uploads/storage/9e9d82d7-4f3b-4538-8114-48926ec45a8c-1.cloud','active',NULL),('JsonResultVerificationSendVO.md','2026-07-13 11:19:21',714,'text/markdown',_binary 'UUUUUUUUUUUUUUUU',_binary '†9\Õ\çJØˆª\æcs\Ò\Ğ','f0c1e91ffdd2c37d93c4a713009814ee0a6ddf147b5f056cfe0eb1fbd947752b',1,_binary 'P\åqgbL\n¡W\Ù77²¬','../Uploads/storage/a6a010cd-e9fd-4999-b956-e69d5a94eb2e-1.cloud','active',NULL),('ShareCreateRequest.md','2026-07-13 11:21:56',889,'text/markdown',_binary 'UUUUUUUUUUUUUUUU',_binary '‡\Ú\ÂOGª¿Ô¦¾¸','996300d4d6f523bf975f369d5db026b15295d67445b6e14b61ddab09015afaa3',1,_binary 'P\åqgbL\n¡W\Ù77²¬','../Uploads/storage/3b5c8d96-9f3d-4265-9acb-3600df334cef-1.cloud','active',NULL),('dns.conf','2026-07-05 14:08:30',49,'application/octet-stream',_binary 'UUUUUUUUUUUUUUUU',_binary '‡\ç\óMùFP’\0	¬\õ¦ü','b9be9c700786b411395a66def0b259670074c32bced59b33be2ddd486abcbe06',1,_binary '­\ß\ÕkzHŒ\á\õ\ô/»:¾','../Uploads/storage/c8f55475-bc31-45dd-919a-25b4b21c5dcf-1.cloud','active',NULL),('JsonResultShareAccessInfoVO.md','2026-07-13 11:19:42',709,'text/markdown',_binary 'UUUUUUUUUUUUUUUU',_binary '‰?b\Ø\Â\ÌCa†=,\ó\è\ì­','fd872d757cab01b8a753a595a3428dd3f2a8cb54780ce83a252b31ed10ffdec1',1,_binary 'P\åqgbL\n¡W\Ù77²¬','../Uploads/storage/66421c0e-0a84-43bb-bbc8-d949c809237a-1.cloud','active',NULL),('JsonResultUserProfileVO.md','2026-07-13 11:20:12',689,'text/markdown',_binary 'UUUUUUUUUUUUUUUU',_binary 'ŠB†\÷•\ïM|>J2N\\\Ö','332b249cf11497e5d031c4bdafde30f3c7cc1b2233d5b2450de38008b453bfeb',1,_binary 'P\åqgbL\n¡W\Ù77²¬','../Uploads/storage/593d0724-9c30-481b-9a70-d3d20193593d-1.cloud','active',NULL),('JsonResultString.md','2026-07-13 11:19:51',641,'text/markdown',_binary 'UUUUUUUUUUUUUUUU',_binary 'ŠÀ\Ë!@GF™FÄ»f@P\Ë','0c936ed15e813a82ca807c285f70bcd3d33e9277bc2e5c8b18e8f413e7fef6c6',1,_binary 'P\åqgbL\n¡W\Ù77²¬','../Uploads/storage/9db83493-6ef0-45f8-9f03-b21d483788f5-1.cloud','active',NULL),('äººå·¥-25å¹´ç§‹æœŸæœ«è€ƒè¯•å­¦ç”Ÿåå•ï¼ˆäººå·¥æ™ºèƒ½å­¦é™¢å¼€è¯¾ï¼‰.xlsx','2026-07-06 10:38:47',214004,'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',_binary 'UUUUUUUUUUUUUUUU',_binary 'I0\ì\òDÍ»\ŞB\Â%\Äs','3e206e065188fc2028a55618bfd508c489e03435020192be1d942fcfe409f6fb',1,_binary '\İ p,	\\H¥¥v\Ñ\è\'ª\Ş','../Uploads/storage/eb75070c-9cd8-409b-857c-84227593844e-1.cloud','active',NULL),('url.conf','2026-07-05 14:08:35',74,'application/octet-stream',_binary 'UUUUUUUUUUUUUUUU',_binary '\É\r@“\'C“G\ÄtŠw°','88e1dca8019ad412cf2c6fbd947a83786cffc7b32f1ee35594d25d1f38fae5f8',1,_binary '­\ß\ÕkzHŒ\á\õ\ô/»:¾','../Uploads/storage/b543832d-886c-43b4-a250-f5371db24704-1.cloud','active',NULL),('ç›®å‰Bç«™æœ€å…¨æœ€ç»†çš„excelé›¶åŸºç¡€å…¨å¥—æ•™ç¨‹ï¼Œ2024æœ€æ–°ç‰ˆï¼ŒåŒ…å«æ‰€æœ‰å¹²è´§ï¼ä¸ƒå¤©å°±èƒ½ä»å°ç™½åˆ°å¤§ç¥ï¼å°‘èµ°99%çš„å¼¯è·¯ï¼å­˜ä¸‹å§ï¼å¾ˆéš¾æ‰¾å…¨çš„ï¼ - 001 - 1.Excel å…¥é—¨.mp4','2026-07-06 20:38:33',17030466,'video/mp4',_binary 'UUUUUUUUUUUUUUUU',_binary '\Ë\ó·M¸¿¢\0A\ã','2b6ebe0533538950bd85fbd0b0064f5a98d3bfe632633bd742b2f3f99728be5b',4,_binary 'ªªªªªªªªªªªªªªª¥','../Uploads/storage/d597d3bb-f85b-4825-a82e-2d472359a1bb-4.cloud','active',NULL),('CarContext.cpython-310.pyc','2026-07-07 11:12:19',615,'application/octet-stream',_binary 'UUUUUUUUUUUUUUUU',_binary '/†<‹L2³\'\é\Ìb*\â','5b77405927962db73c73648a06fab87f13d86d55a9c630c91257c5d88d0aff35',1,_binary 'Øƒ\ÒY\r¼JS¼°\0`¯$','../Uploads/storage/491d5325-0b19-40da-8fe8-3bfaaa9f0b04-1.cloud','active',NULL),('CarConfig.py','2026-07-07 11:12:07',273,'text/x-python-script',_binary 'UUUUUUUUUUUUUUUU',_binary 'µ\õ/fN\ä§Z˜À\Ë\nˆ','44ac1845ddd96fd55a348378dcad0a160ba6c3d6ec08f253ed1014bd452eac6e',1,_binary 't…¯»eµK\"¨\ÍZ›‚•¬','../Uploads/storage/4ba5161e-b935-42b4-b2e1-9a9cfdd17ca7-1.cloud','active',NULL),('dns.jar','2026-07-13 15:35:25',4859,'application/java-archive',_binary 'UUUUUUUUUUUUUUUU',_binary '\Ú\È\ÛE3¢h\ç¥şW\à\Ë','d1150b1831b112b93d74a34a10ce6c11606e0d2255d532c29f91f1d92b40a552',1,_binary '\ËYú˜~@â‘®]%\Ş','../Uploads/storage/8b4e8e35-0069-4f60-82e6-42ed6c63adf6-1.cloud','active',NULL),('NodeVO.md','2026-07-13 11:19:37',712,'text/markdown',_binary 'UUUUUUUUUUUUUUUU',_binary '9û`s\ñDˆ!†xm¨Q','d45aef7ffb8e36c02b6553b720039a6db96f6650b4fecc32ef35666887a94413',1,_binary 'P\åqgbL\n¡W\Ù77²¬','../Uploads/storage/94a0c9d8-32d6-4307-97e2-a27eaa02dd86-1.cloud','active',NULL),('hideme.jar','2026-07-05 14:09:11',7209,'application/java-archive',_binary 'UUUUUUUUUUUUUUUU',_binary '·F\Èl­BÜ—¨k\ÍA\Ì!','fa14c735ab9fed3f3a5df0dc78a5d38ae0a146099ddc858197e9f528bd996c40',1,_binary 'N‡4LŒ DU»A—YGZ','../Uploads/storage/3e0afa98-b01f-4355-b8a0-eda27518e7f4-1.cloud','active',NULL),('power.jar','2026-07-05 14:09:09',9222,'application/java-archive',_binary 'UUUUUUUUUUUUUUUU',_binary 'jµt¤ùIø‹HH\Êl?€v','7819e5b968ce5ea2e638e53d84089d35e89e9ea3088f18f8dbf6dd38d14ab25a',1,_binary 'N‡4LŒ DU»A—YGZ','../Uploads/storage/06d605ab-c337-4ef5-9c21-e33ebfc0fdc2-1.cloud','active',NULL),('base.ts','2026-07-13 11:18:49',2282,'application/octet-stream',_binary 'UUUUUUUUUUUUUUUU',_binary 'ƒÄ»= C\"«c¬›Å\É:','f162ecb194f93c9d87d64692538a60c04faadb00486494658597fd8fdab8bb4c',1,_binary 'ªªªªªªªªªªªªªªª¥','../Uploads/storage/934edb21-cf4e-4294-955f-49af2279c4aa-1.cloud','active',NULL),('CreateUploadsSessionRequest.md','2026-07-13 11:21:47',967,'text/markdown',_binary 'UUUUUUUUUUUUUUUU',_binary '‘8ZJƒM¯£®Nª{-“','653a6f1deacb935c437b5e7a618111244fbd5d75f0c25c0e2788df4b22296bd4',1,_binary 'P\åqgbL\n¡W\Ù77²¬','../Uploads/storage/b2b37075-728a-4d36-b06b-e9f4ad23e37f-1.cloud','active',NULL),('Service.text','2026-07-07 11:12:03',146,'text/plain',_binary 'UUUUUUUUUUUUUUUU',_binary '‘˜fD\ÄXB¥Ÿ/P\ß\\¾\"','0cb91323185b3ca265f17af02cc29268d9996df4280013c7b356ab5f126368bd',1,_binary 'ªªªªªªªªªªªªªªª¥','../Uploads/storage/5ef41ca0-5252-4f32-92be-6b1a04708292-1.cloud','active',NULL),('InternalStorageControllerApi.md','2026-07-13 11:19:16',20379,'text/markdown',_binary 'UUUUUUUUUUUUUUUU',_binary '’\İ\óc‰²I4†1Ô·oŸj','2bac995e071a7c439d8069ee9696021b467438f0ac344b94701dc12884a246be',1,_binary 'P\åqgbL\n¡W\Ù77²¬','../Uploads/storage/6f9ae9a7-e1c2-406a-8a77-41f5cc0370fc-1.cloud','active',NULL),('ja-netfilter.jar','2026-07-13 15:35:50',48639,'application/java-archive',_binary 'UUUUUUUUUUUUUUUU',_binary '˜\ê~\İ\òK\ò‹x\ö…\Ü','3acc4e9d91793f6909458a4761b75b6da45c8868e75dca33c9fec63659202995',1,_binary '‚1_I¶ˆ!rHt\ë”','../Uploads/storage/e8022252-a9a9-4282-92c1-f8fd88341c1c-1.cloud','active',NULL),('ja-netfilter.jar','2026-07-05 14:08:28',48639,'application/java-archive',_binary 'UUUUUUUUUUUUUUUU',_binary '˜¦¹¬plL„„ymûÀ\ë_€','3acc4e9d91793f6909458a4761b75b6da45c8868e75dca33c9fec63659202995',1,_binary 'ªªªªªªªªªªªªªªª¥','../Uploads/storage/27f60ece-295a-41c0-b054-83b82c11e4cf-1.cloud','active',NULL),('package.json','2026-07-13 11:19:01',700,'application/json',_binary 'UUUUUUUUUUUUUUUU',_binary '™*¬K˜MC‚“\Ä\÷\Ğs8','8a847017bf4d63ac3b6e65f07af5d3ed7b327cd8ceed50469e7c95b45c960fd4',1,_binary 'ªªªªªªªªªªªªªªª¥','../Uploads/storage/7fadb565-279b-43f6-a1d6-01882a7f6689-1.cloud','active',NULL),('git_push.sh','2026-07-13 11:18:59',1830,'text/x-sh',_binary 'UUUUUUUUUUUUUUUU',_binary '™ˆ\Ê\ÎSo@7½\ïx½?\ãE½','9b8b2e7dcae7d87ff8c3098e9391771df54d533daca205f6473b7e2c2dce86c6',1,_binary 'ªªªªªªªªªªªªªªª¥','../Uploads/storage/ce94f1cb-8c9c-4433-b7be-c9f8ec28991a-1.cloud','active',NULL),('ç­¾åˆ°è¡¨-å¤§å­¦è‹±è¯­Bï¼ˆ1ï¼‰æ—…ç®¡.xls','2026-07-06 10:39:04',77824,'application/vnd.ms-excel',_binary 'UUUUUUUUUUUUUUUU',_binary '›„~COHAˆ€\ôA¨\Õû7','dddf3c58ac3626435ffdc779dcdb559ba1cbb5b01a858479abdbf197ba0a7c10',1,_binary '\Z\ëd\ÕR\ZA¿³U½Ö­‹¢','../Uploads/storage/104b38f2-5c0d-4ba3-8865-4f558da32cd3-1.cloud','active',NULL),('appcode.vmoptions','2026-07-05 14:08:54',702,'application/octet-stream',_binary 'UUUUUUUUUUUUUUUU',_binary ';ı\ğ\n\ñKR¾Sbt\ä\õ®œ','84a349d678b98359cc6fb13dd63c0cf3b790bce13f6f2b2284726b70386b13f6',1,_binary '­\\Œ\ñ;N\á\É$TĞ \Z','../Uploads/storage/967474ff-17d1-44cb-88c4-c084ae0bbd3f-1.cloud','active',NULL),('studio.vmoptions','2026-07-13 15:36:37',635,'application/octet-stream',_binary 'UUUUUUUUUUUUUUUU',_binary '\ñ”1:.I\ò \÷\á\0¶‰\Ú','58b9de329a0a3786ca2b531abb2b66e91e78e475458faa284133b8fb92938cee',1,_binary '‰º\Ë\÷.\ÄJ¡•uY£\É\ñª/','../Uploads/storage/a4f41111-5da8-486d-8320-fe84fe4db92c-1.cloud','active',NULL),('ç¬¬2æ¬¡å®è®­è¯¾ ç®€å†åˆ¶ä½œ å®è®­æŒ‡å¯¼.docx','2026-07-05 14:01:39',267816,'application/vnd.openxmlformats-officedocument.wordprocessingml.document',_binary 'UUUUUUUUUUUUUUUU',_binary '\\L<¤NÉ‚’\ÈwI“Y','3e6ab437920d0273a3c34f5183132167b872f43eb82c629325bf49816c090d0d',1,_binary 'ù0¢\\@`¤\Ã\nœ\ë\"','../Uploads/storage/565f62bd-0792-491d-a155-d098bb3b516e-1.cloud','active',NULL),('IMG_7098.jpeg','2026-07-05 15:10:07',2191351,'image/jpeg',_binary 'UUUUUUUUUUUUUUUU',_binary 'Ÿ6ˆW`tJ,¢š\'\×\çp¡\ê','66cce55e74ab89abdb55452bb017db12abc074b5d1aa7be976e527f881cd5ecd',1,_binary 'ªªªªªªªªªªªªªªª¥','../Uploads/storage/f27f66db-1abd-494d-ba4d-c7c17072c5bd-1.cloud','active',NULL),('CreateFolderNodeRequest.md','2026-07-13 11:21:09',583,'text/markdown',_binary 'UUUUUUUUUUUUUUUU',_binary 'Ÿy`OsDÒš(Œ\ÅD·0','125a24c6184caa33b681ca3a2303f991758968360f9808a8dd4d7e17edb4a55f',1,_binary 'P\åqgbL\n¡W\Ù77²¬','../Uploads/storage/2c93f5d4-ed51-46e3-974d-68a6665d2e27-1.cloud','active',NULL),('.openapi-generator-ignore','2026-07-13 11:18:51',1040,'application/octet-stream',_binary 'UUUUUUUUUUUUUUUU',_binary 'ŸŸ¶€J\â—)¼±%y\Ô','a6ed8f4e275eee926d27e0c52f30f2d1c4d8409465ac1fbc4511f7cc62de51db',1,_binary 'ªªªªªªªªªªªªªªª¥','../Uploads/storage/dcb32517-4daa-4740-b7e1-ed156f39dca7-1.cloud','active',NULL),('ç­¾åˆ°è¡¨-å¤§å­¦è‹±è¯­Bï¼ˆ1ï¼‰é©¬é™¢.xls','2026-07-06 10:39:09',34304,'application/vnd.ms-excel',_binary 'UUUUUUUUUUUUUUUU',_binary '¡\rql½G%“v†£Æ‰Q\'','b74177de1df959b7ed8c5ab144bda9f082c80d701a7f4f366868c878bfc9246a',1,_binary '\Z\ëd\ÕR\ZA¿³U½Ö­‹¢','../Uploads/storage/d5ff0284-1426-4940-a748-163678b48b7b-1.cloud','active',NULL),('CarEvent.py','2026-07-07 11:12:35',701,'text/x-python-script',_binary 'UUUUUUUUUUUUUUUU',_binary '¢N`~úaHm›,>¡#²\È','4de2dd25e721b26f37bb5f62ebbbf59dee15fa69fc7c23bb3b085aa8bc070a35',1,_binary 't…¯»eµK\"¨\ÍZ›‚•¬','../Uploads/storage/10a32bb4-609c-407c-922e-ab4376e7cfc6-1.cloud','active',NULL),('JsonResultListFileStarVO.md','2026-07-13 11:20:19',699,'text/markdown',_binary 'UUUUUUUUUUUUUUUU',_binary '¢ü\íP,H±\Ì`‡ŒT','dbafde04e2c2d579a430fac74e86aafc744af1173bb7cb2ceaaeacdf6a43132d',1,_binary 'P\åqgbL\n¡W\Ù77²¬','../Uploads/storage/6e3473ba-25ea-47b7-a25b-d33ef4690588-1.cloud','active',NULL),('CarGPSModel.py','2026-07-07 11:12:31',3086,'text/x-python-script',_binary 'UUUUUUUUUUUUUUUU',_binary '¤<\ê¬;\íN\0‘ü\n{™\îƒ','2d5d93659de61534ed00a6df9363440dea33946cb62c521bfff3036bfea32d8d',1,_binary 't…¯»eµK\"¨\ÍZ›‚•¬','../Uploads/storage/a0b6e93e-254d-4b06-98bc-40f206fce161-1.cloud','active',NULL),('CarActionModel.cpython-310.pyc','2026-07-07 11:12:24',5651,'application/octet-stream',_binary 'UUUUUUUUUUUUUUUU',_binary '¥\ö¸‡Cşˆ\ÇÀşšg\Ç\Ç','993f581fe88365b8bba9e3257e9048e05a5ebb7ecf42a2f9664f42a78a6044bd',1,_binary 'Øƒ\ÒY\r¼JS¼°\0`¯$','../Uploads/storage/44678112-c8e5-4a60-993b-8d944393e938-1.cloud','active',NULL),('TrashControllerApi.md','2026-07-13 11:21:07',14951,'text/markdown',_binary 'UUUUUUUUUUUUUUUU',_binary '§“S›AOº\ÆI†z^\Z\n','414b4579272f27ddba912fa214f1d385a330a930d0dffb06a4656a342afdcd24',1,_binary 'P\åqgbL\n¡W\Ù77²¬','../Uploads/storage/6482df6e-3f91-48b4-a1d8-ae6228684f6c-1.cloud','active',NULL),('IMG_7009.jpeg','2026-07-05 15:16:24',4626559,'image/jpeg',_binary 'UUUUUUUUUUUUUUUU',_binary '§U6w_\ÎB\\¦1 W','d29c750ff0e139fd92f01f685b1f5179e798218868e4fb76107602c657e0222c',1,_binary 'ªªªªªªªªªªªªªªª¥','../Uploads/storage/7b128c8b-4feb-4a42-bad0-0b4de503c538-1.cloud','active',NULL),('dns.jar','2026-07-13 15:37:08',4859,'application/java-archive',_binary 'UUUUUUUUUUUUUUUU',_binary '¨E¾¹ÿTN6¿\Üq\íx2','d1150b1831b112b93d74a34a10ce6c11606e0d2255d532c29f91f1d92b40a552',1,_binary '\ã•°Ö¢O\óƒ\\F._.','../Uploads/storage/571a1e7e-5184-49a2-ac8d-bb363b6e0f2e-1.cloud','active',NULL),('jetbrainsclient.vmoptions','2026-07-13 15:37:01',636,'application/octet-stream',_binary 'UUUUUUUUUUUUUUUU',_binary 'ªn _KÁ–\Â²šG','f5a4f7aec84eb0a45dd6abe9d4bfd2096e4bab775855f1447dc1bf8346c35b28',1,_binary '‰º\Ë\÷.\ÄJ¡•uY£\É\ñª/','../Uploads/storage/7fc63f0b-5266-4551-a837-c64cec02ee32-1.cloud','active',NULL),('FolderNodeVO.md','2026-07-13 11:19:33',724,'text/markdown',_binary 'UUUUUUUUUUUUUUUU',_binary 'ªÿ\r6¡NÚ”z¹k¬','e98bba3b33711e97dae617f603b04559b907a784a30ee31dbfbcb99b895d098d',1,_binary 'P\åqgbL\n¡W\Ù77²¬','../Uploads/storage/1b4e91cd-a74a-4db9-b46b-784fc24dee1d-1.cloud','active',NULL),('install.sh','2026-07-05 14:08:45',3498,'text/x-sh',_binary 'UUUUUUUUUUUUUUUU',_binary '­rÁc»G’‚v\"Q\ØT.','ff7b76ed04b0ca7e42b380fd3426b4ea14dd1e6fd39154fcd32ef9e11907478f',1,_binary 'ÅˆĞ£3\ÎL1›\İ\á\rK\á©\ò','../Uploads/storage/97432ff4-0eb1-48ad-80c5-3f9f252ba60c-1.cloud','active',NULL),('ç›®å‰Bç«™æœ€å…¨æœ€ç»†çš„excelé›¶åŸºç¡€å…¨å¥—æ•™ç¨‹ï¼Œ2024æœ€æ–°ç‰ˆï¼ŒåŒ…å«æ‰€æœ‰å¹²è´§ï¼ä¸ƒå¤©å°±èƒ½ä»å°ç™½åˆ°å¤§ç¥ï¼å°‘èµ°99%çš„å¼¯è·¯ï¼å­˜ä¸‹å§ï¼å¾ˆéš¾æ‰¾å…¨çš„ï¼ - 001 - 1.Excel å…¥é—¨.mp4','2026-07-07 10:44:10',17030466,'video/mp4',_binary 'UUUUUUUUUUUUUUUU',_binary '­:³QaF †NT\ÆûÜ›','2b6ebe0533538950bd85fbd0b0064f5a98d3bfe632633bd742b2f3f99728be5b',4,_binary '­\ß\ÕkzHŒ\á\õ\ô/»:¾','../Uploads/storage/2409e541-6116-49c2-8db4-69ea5e0f1d37-4.cloud','active',NULL),('æ–‡å­¦ä¸ä¼ åª’å­¦é™¢ å¤§å­¦è‹±è¯­Bï¼ˆ1ï¼‰.xls','2026-07-06 10:39:20',83968,'application/vnd.ms-excel',_binary 'UUUUUUUUUUUUUUUU',_binary '­vxYC\ÚEš¡\0<	\ÔO','eed72a84d95a0d0124e4748668a6c4b74b0643242c4704d3bd69605ac9cca1c8',1,_binary '\Z\ëd\ÕR\ZA¿³U½Ö­‹¢','../Uploads/storage/422b9bf0-d54b-476e-9d5e-c77d360b7161-1.cloud','active',NULL),('install-all-users.vbs','2026-07-13 15:36:02',2426,'application/octet-stream',_binary 'UUUUUUUUUUUUUUUU',_binary '®|B¥˜³FÊ“\0x\çù&‘','26a6ab6fa87ade5e2384bd539bcd8f01e9400b3ab636de9843c92b8099c96493',1,_binary 'iJr\áD¼²‰¥\Ë\ãW²','../Uploads/storage/57cd53d6-04df-4346-bc54-54fcaeff1456-1.cloud','active',NULL),('REST API basics- CRUD, test & variable.postman_collection.json','2026-06-13 19:37:59',21243,'application/json',_binary 'UUUUUUUUUUUUUUUU',_binary '°o\÷¦f_D‡„\r*/¤%\'p','6265c07867f05e56e4b44e95de27ceb376b7b918c2a0dd31fe21ce78e567286d',1,_binary 'ªªªªªªªªªªªªªªª¥','../Uploads/storage/8628811f-8e55-4a04-a861-bc769ae860a9-1.cloud','deleted',NULL),('ShareContentItemVO.md','2026-07-13 11:21:12',886,'text/markdown',_binary 'UUUUUUUUUUUUUUUU',_binary '³4Ct)@p²\rV\"*I˜','8811eda8d99ec48a2662266f2c504e31bae31b975cb84644ad5c5b16e30e4545',1,_binary 'P\åqgbL\n¡W\Ù77²¬','../Uploads/storage/73c377eb-531f-47c6-b0ca-88dcecc3ba18-1.cloud','active',NULL),('ç§æœ‰äº‘ç›˜ç³»ç»Ÿ - å®Œæ•´APIæµ‹è¯•-documentation.html','2026-06-14 05:21:00',39838,'text/html',_binary 'UUUUUUUUUUUUUUUU',_binary '³b\åFIÚ¹<¡\Ø@!','f4cfaace78a88f6911833f1f41cf20b1b46e56006d2003d8dc2797420049db3d',1,_binary 'ªªªªªªªªªªªªªªª¥','../Uploads/storage/376947d9-3cb3-47a7-b76d-89f3f020f9a2-1.cloud','deleted',NULL),('FILES','2026-07-13 11:19:13',2276,'application/octet-stream',_binary 'UUUUUUUUUUUUUUUU',_binary '³n=¶Kƒ©\Zˆ\ö\Æú','5eb7f127e7d9b00600c4ae1907e251b362e8771a0a743f4c64e549d4309a6751',1,_binary 'ò•†ŠV\ÆOÙ†]øs¢4','../Uploads/storage/a27fa97a-50a9-4be9-ab5f-e72ae03745c4-1.cloud','active',NULL),('æœé‚¦åˆ†æ.xlsx','2026-07-08 19:17:30',256192,'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',_binary 'UUUUUUUUUUUUUUUU',_binary '´!\Ø4\ØG²ƒÅ‚q·','c6b3e2c21a01464b382d32ac0355dd1d231748b21fba910efb332d4f3bfa5d5a',1,_binary 'ªªªªªªªªªªªªªªª¥','../Uploads/storage/45e3fa8f-4db6-4dab-97d9-9073860cd9ea-1.cloud','active',NULL),('IMG_7116.jpeg','2026-07-05 15:09:49',1800650,'image/jpeg',_binary 'UUUUUUUUUUUUUUUU',_binary '´D¿\Öe\õNÁ›Æ²w.:¦','b7a638b6f82c5ae6f1e7ef2f1515eaeab1578abbe485759a98e832673de8b108',1,_binary 'ªªªªªªªªªªªªªªª¥','../Uploads/storage/753d209b-76b0-4858-82fc-5468ee2dc66e-1.cloud','active',NULL),('install.sh','2026-07-13 15:36:13',3498,'text/x-sh',_binary 'UUUUUUUUUUUUUUUU',_binary 'µm¥¸„]G…®˜œ£¿\ÃÆ—','ff7b76ed04b0ca7e42b380fd3426b4ea14dd1e6fd39154fcd32ef9e11907478f',1,_binary 'iJr\áD¼²‰¥\Ë\ãW²','../Uploads/storage/74ecd475-a98f-4998-acc2-36fcd3bb4632-1.cloud','active',NULL),('power.jar','2026-07-13 15:35:27',9222,'application/java-archive',_binary 'UUUUUUUUUUUUUUUU',_binary '¶Uÿe5¸Of­°\à oş\è\Ç','7819e5b968ce5ea2e638e53d84089d35e89e9ea3088f18f8dbf6dd38d14ab25a',1,_binary '\ËYú˜~@â‘®]%\Ş','../Uploads/storage/5cb53540-4953-4012-a011-9d03d9a402cc-1.cloud','active',NULL),('VerificationControllerApi.md','2026-07-13 11:19:56',6244,'text/markdown',_binary 'UUUUUUUUUUUUUUUU',_binary '·\Õ@.1®\ê\ï–\Û\Ì','79a1db5ebbbb3014bb5fe4cf7a99fd78e2ab7ecb83cf51e91894d9446d97877c',1,_binary 'P\åqgbL\n¡W\Ù77²¬','../Uploads/storage/6a422925-4599-47e7-83b5-84ca637c55f5-1.cloud','active',NULL),('166180-gary-hai_mian_bao_bao-ni_ke-ka_tong-kuai_le_de-3840x2160.jpg','2026-07-05 10:37:46',6463141,'image/jpeg',_binary 'UUUUUUUUUUUUUUUU',_binary '·|u×ƒ\ëG\ô½£UBz1k','39b2b597216b21e7f155949f209ef412a487226f1571650c87de04e2e2549021',2,_binary 'ù0¢\\@`¤\Ã\nœ\ë\"','../Uploads/storage/4fecd2d2-1b2a-487c-9dd1-5cf1b281d604-2.cloud','active',NULL),('MoveFileRequest.md','2026-07-13 11:20:38',499,'text/markdown',_binary 'UUUUUUUUUUUUUUUU',_binary '¸T\òƒ˜tNG¾\İ\Ø`\Ì\ÈÀ–','31a2c97c10026040b4c6ce9e444a44334d36122cfd0ade57ef6470d7a077db76',1,_binary 'P\åqgbL\n¡W\Ù77²¬','../Uploads/storage/39521c99-8164-4b8a-8035-a5024bcedf54-1.cloud','active',NULL),('NS4150C.pdf','2026-07-08 19:16:53',3921433,'application/pdf',_binary 'UUUUUUUUUUUUUUUU',_binary '¹¿3F@C™zÿ_˜\á\Ñ','c44371ece8e6b7c79853511e4205175ff22d97617aaed8f24a693c9694c9e227',1,_binary 'ªªªªªªªªªªªªªªª¥','../Uploads/storage/30f6edf1-d3da-4104-a43a-33689908a38e-1.cloud','active',NULL),('ç›®å‰Bç«™æœ€å…¨æœ€ç»†çš„excelé›¶åŸºç¡€å…¨å¥—æ•™ç¨‹ï¼Œ2024æœ€æ–°ç‰ˆï¼ŒåŒ…å«æ‰€æœ‰å¹²è´§ï¼ä¸ƒå¤©å°±èƒ½ä»å°ç™½åˆ°å¤§ç¥ï¼å°‘èµ°99%çš„å¼¯è·¯ï¼å­˜ä¸‹å§ï¼å¾ˆéš¾æ‰¾å…¨çš„ï¼ - 001 - 1.Excel å…¥é—¨.mp4','2026-07-06 20:13:29',17030466,'video/mp4',_binary 'UUUUUUUUUUUUUUUU',_binary '¹†!iF“Œ~ÿ\Ğ\ÒR','2b6ebe0533538950bd85fbd0b0064f5a98d3bfe632633bd742b2f3f99728be5b',4,_binary '­\\Œ\ñ;N\á\É$TĞ \Z','../Uploads/storage/1207fbe7-4485-4587-a34b-d95495d0bad2-4.cloud','active',NULL),('datagrip.vmoptions','2026-07-13 15:36:56',634,'application/octet-stream',_binary 'UUUUUUUUUUUUUUUU',_binary '¹[\Ò-\Ò\"OqŒ@\Ò\ô\èg','c03bf5ae617eae087cf21a339dd956ce1260d11863e60a21b8b0365f4afdc7d5',1,_binary '‰º\Ë\÷.\ÄJ¡•uY£\É\ñª/','../Uploads/storage/bbe38c66-b3bb-4278-ab51-22770915b563-1.cloud','active',NULL),('LoginDeviceVO.md','2026-07-13 11:20:56',905,'text/markdown',_binary 'UUUUUUUUUUUUUUUU',_binary '»ˆ/4\ã›Is…Zû·Å“','13864f25cff544614f079c5616bf388ce3bc74da429ddea32f4284a49632a7e6',1,_binary 'P\åqgbL\n¡W\Ù77²¬','../Uploads/storage/e30eac5f-1002-4676-8e1d-d2a3541a6f1a-1.cloud','active',NULL),('power.conf','2026-07-05 14:08:32',6854,'application/octet-stream',_binary 'UUUUUUUUUUUUUUUU',_binary '¼¬\àÏ‰]M–\é\Ò¿^Š','05c70700cb0a3e8e7c596205cebe7284b2f7fbdf5f6beeb4424730a753239701',1,_binary '­\ß\ÕkzHŒ\á\õ\ô/»:¾','../Uploads/storage/c7b51746-9d24-46b3-9980-e97cc1bda5fe-1.cloud','active',NULL),('é™„ä»¶2ï¼š2025å¹´ç§‹å­£å­¦æœŸæœŸæœ«è€ƒè¯•å®‰æ’è¡¨ï¼ˆæ•°å­¦ä¸å¤§æ•°æ®å­¦é™¢ï¼‰ - - é«˜ç­‰æ•°å­¦.xlsx','2026-07-06 10:39:53',25964,'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',_binary 'UUUUUUUUUUUUUUUU',_binary '¼\ïs9M=L\ò‡\ÉC!\\\İ\÷','bb58f7d572accc56ca88851c44c4e7747c14a4699c9e54373715f92a59bf3cbc',1,_binary '\nh¯H\ÚMĞŸ·3Û >«','../Uploads/storage/46234fe1-c16e-4995-8644-2ae6599dd557-1.cloud','active',NULL),('CarWebRTCModel.cpython-310.pyc','2026-07-07 11:12:28',5607,'application/octet-stream',_binary 'UUUUUUUUUUUUUUUU',_binary '¾4\É_zJs˜§#-&²Ÿm','5892728641fd9e5b00927a7843c75c1443f3369cfa25099b412e37088a239400',1,_binary 'Øƒ\ÒY\r¼JS¼°\0`¯$','../Uploads/storage/4dfc4daa-1e96-4c17-9227-96452cb5a92e-1.cloud','active',NULL),('JsonResultListString.md','2026-07-13 11:21:32',666,'text/markdown',_binary 'UUUUUUUUUUUUUUUU',_binary '¾:‰À¸TK‚¸»G\â\å\âü“','45c92df58b234f6fd0f285833b3f9373526f3abefe7226d48edf5527fef30b5f',1,_binary 'P\åqgbL\n¡W\Ù77²¬','../Uploads/storage/cb84cfc0-5be7-49d9-bff3-e7e55dfe2651-1.cloud','active',NULL),('.DS_Store','2026-07-06 10:38:52',6148,'application/octet-stream',_binary 'UUUUUUUUUUUUUUUU',_binary '¾s\ğ\'³O¢?\0l¶ÿ','7da2584d9480ae4a1adcacd260c36820da58dc089ef42984ea3dee11063265c7',1,_binary '\Z($\ò¦\rO0ƒ\ï\â\0\ó\Í\ì','../Uploads/storage/4d480d18-290a-4094-936d-894b020f36e9-1.cloud','active',NULL),('JsonResultTrashTargetVO.md','2026-07-13 11:21:34',689,'text/markdown',_binary 'UUUUUUUUUUUUUUUU',_binary '\ÂVİ³\òzFP¾¤mDl°','19f75a555e39dcfdb0fb3cf99c300084baf075a0083c3bfbe1c89124f0d9c529',1,_binary 'P\åqgbL\n¡W\Ù77²¬','../Uploads/storage/d35a2350-0ae3-42f7-b0d1-4e2186dc320e-1.cloud','active',NULL),('.DS_Store','2026-07-06 10:38:50',8196,'application/octet-stream',_binary 'UUUUUUUUUUUUUUUU',_binary 'Ã:‰\Ù!F<£\ÏQz*Š','06a233205d7fa3639e0cc502fe0b944703bc71c3b03a222377803ee52bf626ab',1,_binary '\İ p,	\\H¥¥v\Ñ\è\'ª\Ş','../Uploads/storage/43abe89b-d607-4d18-ae93-ce590fa3c177-1.cloud','active',NULL),('iMovie å‰ªè¾‘èµ„æºåº“.imovielibrary.zip','2026-07-05 10:01:29',22718,'application/zip',_binary 'UUUUUUUUUUUUUUUU',_binary '\Ä\Æ]?gBÜ¯ÍŸœm\Ù|','9cf1290a52abe60a2c02511b3d0855e68851d7179ecb118b1b333b9a1eeb6aac',1,_binary 'ªªªªªªªªªªªªªªª¥','../Uploads/storage/d00847ef-eeeb-4510-a070-cd6ad879fbf4-1.cloud','active',NULL),('webstorm.vmoptions','2026-07-05 14:08:50',702,'application/octet-stream',_binary 'UUUUUUUUUUUUUUUU',_binary '\ÅZ»‰‚¸Fü¼·b«s›\Ç','84a349d678b98359cc6fb13dd63c0cf3b790bce13f6f2b2284726b70386b13f6',1,_binary '­\\Œ\ñ;N\á\É$TĞ \Z','../Uploads/storage/9922a908-4276-4e7a-af58-4200cce1d247-1.cloud','active',NULL),('FileSearchControllerApi.md','2026-07-13 11:20:14',1564,'text/markdown',_binary 'UUUUUUUUUUUUUUUU',_binary '\Åv\Zw’A¢.¸h4n\â\Ò','1bb1b5e0f15ea6c894bda26abf60f87f8d4491f2622a7d9543000f44a4bbd47b',1,_binary 'P\åqgbL\n¡W\Ù77²¬','../Uploads/storage/e528fdd7-627d-4adc-afec-ca53242fe7b7-1.cloud','active',NULL),('JsonResultListNodeVO.md','2026-07-13 11:20:54',679,'text/markdown',_binary 'UUUUUUUUUUUUUUUU',_binary '\Å\Ødb¯N€½e¬ƒ\Zll','cb8f904b6e8aa70799a30d4c1ae8f6ffb49d2ef233044e2b48326982b573324e',1,_binary 'P\åqgbL\n¡W\Ù77²¬','../Uploads/storage/2a755ad7-20a3-4502-adae-632c3cacdafc-1.cloud','active',NULL),('JsonResultVoid.md','2026-07-13 11:20:32',635,'text/markdown',_binary 'UUUUUUUUUUUUUUUU',_binary '\Æ2\äL\ô\ñL™\0\Ü$Ü´\Ã','215ca0d33661b3b16410105c2d18f63420247945cede4fe614450729c0a5e7c3',1,_binary 'P\åqgbL\n¡W\Ù77²¬','../Uploads/storage/07e5d300-9492-4ef1-b904-9208e6628b99-1.cloud','active',NULL),('.DS_Store','2026-07-05 10:02:48',6148,'application/octet-stream',_binary 'UUUUUUUUUUUUUUUU',_binary '\Æ5­$K\ãEF©\İ%\Ü>r','79ba9d9c828f616b82034ed91ac01877c64006ddf3c57d99623c9ebbcde5e42e',1,_binary 'ªªªªªªªªªªªªªªª¥','../Uploads/storage/4c5e9e51-3aec-419d-869c-1043c7e1c518-1.cloud','active',NULL),('power.conf','2026-07-13 15:35:56',6854,'application/octet-stream',_binary 'UUUUUUUUUUUUUUUU',_binary 'Ç°\í\0*HSˆª\ñ·pT\İ\Ä','05c70700cb0a3e8e7c596205cebe7284b2f7fbdf5f6beeb4424730a753239701',1,_binary 'ÿÂB=Eg‚&\Z\õz\ë','../Uploads/storage/a8a2938f-d15a-418b-ba93-d4b9720faaef-1.cloud','active',NULL),('JsonResultShareLinkEntity.md','2026-07-13 11:20:21',699,'text/markdown',_binary 'UUUUUUUUUUUUUUUU',_binary '\É3T\ÍVB,ƒjo\â¤g’ ','dc4278a68e0fc167576aaad3dcebe225b41a812727cc4a03bdfa54ca4732843c',1,_binary 'P\åqgbL\n¡W\Ù77²¬','../Uploads/storage/c176e8af-e946-4ba5-8d51-40977ceda77e-1.cloud','active',NULL),('è€ƒåœºè€ƒè¯•å®‰æ’ï¼ˆè€ƒç”Ÿç­¾åˆ°è¡¨ï¼‰é©¬å…‹æ€ä¸»ä¹‰åŸºæœ¬åŸç†.xls','2026-07-06 10:39:33',239616,'application/vnd.ms-excel',_binary 'UUUUUUUUUUUUUUUU',_binary '\É!„n\Ñ\Ò@#‡\î€)\á\Ş/I','643d1b3e6eafd31e0984695f30cba1840a1c1bd9e532e5c079a0f816acc63bad',1,_binary '³¢\ÂAx\ôAG\öˆœ','../Uploads/storage/da7316f4-5033-46e5-ad1d-9c311f783ee1-1.cloud','active',NULL),('jetbrains_client.vmoptions','2026-07-13 15:36:53',635,'application/octet-stream',_binary 'UUUUUUUUUUUUUUUU',_binary '\É5–\âUSH\ï¡l€»h\ß','58b9de329a0a3786ca2b531abb2b66e91e78e475458faa284133b8fb92938cee',1,_binary '‰º\Ë\÷.\ÄJ¡•uY£\É\ñª/','../Uploads/storage/ffcee128-ce41-4a3d-930b-60d8f35b72a3-1.cloud','active',NULL),('FileSearchRequest.md','2026-07-13 11:21:14',1312,'text/markdown',_binary 'UUUUUUUUUUUUUUUU',_binary '\É\ê\ğ\Õ\Û)C¨‰—c$\Ğ','ad66295374c0ca950482e48b6637cd2456d2bf94e9e5e7370757f37bab47eb5b',1,_binary 'P\åqgbL\n¡W\Ù77²¬','../Uploads/storage/8ac50d51-0e57-45d0-87ac-272c171e5585-1.cloud','active',NULL),('common.ts','2026-07-13 11:18:54',5262,'application/octet-stream',_binary 'UUUUUUUUUUUUUUUU',_binary 'Ê™­Å‘\ÛG…¾”¦\÷\\)\'À','6b96507d759ae685e110b0cc01600cbd0460863c1f7e4b7937a8a66041cee313',1,_binary 'ªªªªªªªªªªªªªªª¥','../Uploads/storage/82af1fc5-8304-4c9e-94cd-277505149994-1.cloud','active',NULL),('è€ƒåœºè€ƒè¯•å®‰æ’ï¼ˆè€ƒç”Ÿç­¾åˆ°è¡¨ï¼‰ä¸­å›½è¿‘ç°ä»£å²çº²è¦.xls','2026-07-06 10:39:40',231936,'application/vnd.ms-excel',_binary 'UUUUUUUUUUUUUUUU',_binary 'Ê«M_\õEÖ«“_›ºw/\n','5467c0aa8e63c9616782b639bca06ede334df5fa663c54d049f4d47ed74d70c3',1,_binary '³¢\ÂAx\ôAG\öˆœ','../Uploads/storage/a75741c5-d8ae-4174-8ddf-ec2724a44ebf-1.cloud','active',NULL),('UserControllerApi.md','2026-07-13 11:21:23',10711,'text/markdown',_binary 'UUUUUUUUUUUUUUUU',_binary 'Ì\Ùoı\ÚJÕ‹…´\n«\õ~V','2ce7ee0311f462855c8f9b940889a7a645addedf6795f00bca5685f370e9f8f3',1,_binary 'P\åqgbL\n¡W\Ù77²¬','../Uploads/storage/42482f6c-7261-497a-8fb1-f4e0944377f1-1.cloud','active',NULL),('ç”µå•†ç®¡ç†åå° API æ¥å£æ–‡æ¡£.md','2026-07-05 10:32:11',56070,'text/markdown',_binary 'UUUUUUUUUUUUUUUU',_binary 'Ï¾;xÁIxªşN\n ˆ©U','3b8443e9d932c0ed046b45c99aae35fa5aad7e1468da2ebe4eb2e0807ec67243',1,_binary 'ªªªªªªªªªªªªªªª¥','../Uploads/storage/eb8ac50f-24ef-4063-8523-ba2b4532c055-1.cloud','active',NULL),('api.ts','2026-07-13 11:18:57',417757,'application/octet-stream',_binary 'UUUUUUUUUUUUUUUU',_binary '\Ï\ĞÇ´ÂCÃ¦<\ğ\îül_','063a15e34589c3f712bedb5d9a7f22f3c064277598092babe91ede01b42245ba',1,_binary 'ªªªªªªªªªªªªªªª¥','../Uploads/storage/e11f0f3c-8858-4ceb-9740-e499e916d0a2-1.cloud','active',NULL),('pycharm.vmoptions','2026-07-13 15:36:27',635,'application/octet-stream',_binary 'UUUUUUUUUUUUUUUU',_binary '\ĞN\â¤leHã¦&’\öà§’','58b9de329a0a3786ca2b531abb2b66e91e78e475458faa284133b8fb92938cee',1,_binary '‰º\Ë\÷.\ÄJ¡•uY£\É\ñª/','../Uploads/storage/1675b566-8a20-4e68-8673-f44914ae2cf6-1.cloud','active',NULL),('index.ts','2026-07-13 11:19:06',951,'application/octet-stream',_binary 'UUUUUUUUUUUUUUUU',_binary '\Ò,fy/hHK¨©£\Z\Ù#¯F','45a1dc6e6d0f20fb7996ba063a21e05eefe1fc88c95829fcb212728651995638',1,_binary 'ªªªªªªªªªªªªªªª¥','../Uploads/storage/6942702c-98b3-41a6-a36f-bcd0a03cfff6-1.cloud','active',NULL),('i.py','2026-07-07 11:12:51',1408,'text/x-python-script',_binary 'UUUUUUUUUUUUUUUU',_binary 'Ô‡\Ø\rW\rBø°\ÍB\"+F','2ec8486935d089fc63fa6b3d4a77305c76c98d3700db7f2e85e1545953c60187',1,_binary 'ªªªªªªªªªªªªªªª¥','../Uploads/storage/9b108c78-3583-41e0-a91f-e6c079f50600-1.cloud','active',NULL),('ES8311v1.1å¯è§†åŒ–ibom_ä½¿ç”¨æµè§ˆå™¨æ‰“å¼€.zip','2026-07-08 19:23:23',220790,'application/zip',_binary 'UUUUUUUUUUUUUUUU',_binary '\Ô\Ü\Ë\ëˆeEº½¸rrûz','545962ffbbb1f00af57a748d23abb15de393a775d572d45d2eaa583847d3c063',1,_binary 'ªªªªªªªªªªªªªªª¥','../Uploads/storage/c67f6c70-60c7-4794-b3f1-385964079df2-1.cloud','active',NULL),('é™„ä»¶2ï¼š2025å¹´ç§‹å­£å­¦æœŸæœŸæœ«è€ƒè¯•å®‰æ’è¡¨ï¼ˆæ•°å­¦ä¸å¤§æ•°æ®å­¦é™¢ï¼‰ - - é«˜ç­‰æ•°å­¦C1.xlsx','2026-07-06 10:39:47',37156,'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',_binary 'UUUUUUUUUUUUUUUU',_binary '\Ô\éQ§\ï,Gf-\ì…\âªKS','8b33e6056a7a9b22e72d80da8f13bd22531f7e8280a6015e254034ab42c848d5',1,_binary '\nh¯H\ÚMĞŸ·3Û >«','../Uploads/storage/3ba75a3e-c992-47a2-b1e3-9678af432e62-1.cloud','active',NULL),('1.mp4','2026-07-06 15:08:57',30249196,'video/mp4',_binary 'UUUUUUUUUUUUUUUU',_binary '\Õj\'\"F­A7©L•\Ä·KH','0d06a9f72d5154b641110fa801dc2cc7932af0ab96ee5dfb34df03b63b1722a2',6,_binary 'ªªªªªªªªªªªªªªª¥','../Uploads/storage/f9e06835-3166-46e2-8057-7b1a76524389-6.cloud','active',NULL),('JsonResultFileSearchVO.md','2026-07-13 11:21:45',684,'text/markdown',_binary 'UUUUUUUUUUUUUUUU',_binary '\Ö}Ş„NB \ÌtB\Z\ô±\È','5caf8a6cded8d748fc133b918c8f6c2aa57ed92e77188a918ee66961a5d559a4',1,_binary 'P\åqgbL\n¡W\Ù77²¬','../Uploads/storage/21e8ae71-e350-4cbe-9078-ba7e008a1414-1.cloud','active',NULL),('.DS_Store','2026-07-13 15:35:45',6148,'application/octet-stream',_binary 'UUUUUUUUUUUUUUUU',_binary 'Ù®CeıvBÏ®ƒ¸Å†\÷¤l','0043378178319b711b434d82fca61c06fd4be69e59344da2fce31b78552517a4',1,_binary '1\ÜfCJ>§jO‡µ\Ë©','../Uploads/storage/16bdc40c-541f-49cd-8009-38b7884ec2af-1.cloud','active',NULL),('JsonResultShareLinkVO.md','2026-07-13 11:19:49',679,'text/markdown',_binary 'UUUUUUUUUUUUUUUU',_binary '\Ú\Ú\ßZ)Em¸\ßb$4=\Ï','644a372754f825e2978d583537e407e7af2527cfbc845acd5c0558362e458792',1,_binary 'P\åqgbL\n¡W\Ù77²¬','../Uploads/storage/01f8b14d-a555-437d-98dd-454d61ded1c2-1.cloud','active',NULL),('.gitig','2026-07-05 10:02:53',1799,'application/octet-stream',_binary 'UUUUUUUUUUUUUUUU',_binary '\Û\Ç\n¦\ÓsGÍ¹M/É£o.','79b6f8054f8ef5e9e78c18174bf57caf29b11410166b9268d6923e87520eb88f',1,_binary 'ªªªªªªªªªªªªªªª¥','../Uploads/storage/c3cc88ef-a879-4358-842f-a9712309d5b0-1.cloud','active',NULL),('ä½¿ç”¨è¯´æ˜æ–‡æ¡£.txt','2026-07-13 15:35:09',437,'text/plain',_binary 'UUUUUUUUUUUUUUUU',_binary '\Ü#\ô¤?\òA{±PF!†ƒm','1609f9b5fd0617e7d26a087e7d0ea6b7efb4f716b195618ea611a0734644c116',1,_binary 'ªªªªªªªªªªªªªªª¥','../Uploads/storage/8610080d-8762-4565-bac7-71469af43345-1.cloud','active',NULL),('POS3-es8311.csv','2026-07-13 11:10:18',2213,'text/csv',_binary 'UUUUUUUUUUUUUUUU',_binary '\İt*¶¡N‘ŸaXh¥\÷¿','bfc84f200a0d427dea3712e83b13e41731be4ee91eaa58aecd7196f3d7305f69',1,_binary 'ªªªªªªªªªªªªªªª¥','../Uploads/storage/f0899ffa-f00c-44ec-83f5-124595f38337-1.cloud','active',NULL),('JsonResultFileEntity.md','2026-07-13 11:20:23',674,'text/markdown',_binary 'UUUUUUUUUUUUUUUU',_binary '\İø2\ôLWCf›ºUB2­','a22450e9db809995e0ff932694d8609cfca9f37f2d0a0faf6eb1de38f1a1dc8f',1,_binary 'P\åqgbL\n¡W\Ù77²¬','../Uploads/storage/ccef3f9a-5780-4d1b-a63a-5b6d587f4223-1.cloud','active',NULL),('ide-eval-resetter-2.1.6.zip','2026-07-13 15:37:19',30838,'application/zip',_binary 'UUUUUUUUUUUUUUUU',_binary '\ß\ôPaĞŸAŸ,Mcgşa','c411c37412f8e55d3fd6e488a7a45a662fdf1788c7b5c64fb6a7093c9baf1652',1,_binary '¿\á£=z,E\ïœ‹ÀW?}','../Uploads/storage/6e864fa1-946c-4a2d-873b-faecc7edeace-1.cloud','active',NULL),('è€ƒåœºè€ƒè¯•å®‰æ’ï¼ˆè€ƒç”Ÿç­¾åˆ°è¡¨ï¼‰æ¯›æ³½ä¸œæ€æƒ³å’Œä¸­å›½ç‰¹è‰²ç¤¾ä¼šä¸»ä¹‰ç†è®ºä½“ç³»æ¦‚è®º.xls','2026-07-06 10:39:38',222720,'application/vnd.ms-excel',_binary 'UUUUUUUUUUUUUUUU',_binary '\ß\õ ~”E§«\Ã9Q?','b13ca5b48c82d89cae1f08f80afb82632de2099eff3e5b05ddebf72a178c7695',1,_binary '³¢\ÂAx\ôAG\öˆœ','../Uploads/storage/969a0bba-14dd-4ad4-8c1d-fb63d82b6a8c-1.cloud','active',NULL),('tox.ini','2026-07-13 15:33:13',149,'application/octet-stream',_binary 'UUUUUUUUUUUUUUUU',_binary 'à¶©\0Y‹C¦³¤ü\î\ÈŞ½','42325fa7e38273cfd194ad1a41aa6e2c8d6780355d7a1058d28fdf8231e7254a',1,_binary 'N‡4LŒ DU»A—YGZ','../Uploads/storage/7ca1cde7-098a-4ed7-bae8-0436da48b95e-1.cloud','active',NULL),('JsonResultQuotaVO.md','2026-07-13 11:20:41',659,'text/markdown',_binary 'UUUUUUUUUUUUUUUU',_binary '\à\ês3ceLÊ\à\ó^¼B','9d737ca34804fc9dd4ee0bdb6e36e609d9dfd2e13c09151e6d84d057b796f6c9',1,_binary 'P\åqgbL\n¡W\Ù77²¬','../Uploads/storage/11905286-26a7-41d9-9fd0-e5ff8cfcab5a-1.cloud','active',NULL),('å¤§å­¦è‹±è¯­B(1) ç­¾åˆ°è¡¨ï¼ˆç»ç®¡ï¼‰.xls','2026-07-06 10:39:01',79872,'application/vnd.ms-excel',_binary 'UUUUUUUUUUUUUUUU',_binary '\âŒ&Ÿ\åJ‘‡\å+gL\Óu','c62b84c48b6418e3efe5f5b1321d9e7323e7425d6f370fdc0ed8d0933afd1ac8',1,_binary '\Z\ëd\ÕR\ZA¿³U½Ö­‹¢','../Uploads/storage/1d64a2fa-da9d-4431-b83c-a8093f8db92b-1.cloud','active',NULL),('RenameFileRequest.md','2026-07-13 11:19:58',503,'text/markdown',_binary 'UUUUUUUUUUUUUUUU',_binary '\âÀQ¥rOz¿5}\÷ >ø','dc702638b513f4f0a9440f75f5b6cae0a5e7c13235c82a2c238d66c389b8ba14',1,_binary 'P\åqgbL\n¡W\Ù77²¬','../Uploads/storage/b32b016f-b861-40dd-bcbb-df135b900679-1.cloud','active',NULL),('FolderFileInfo.md','2026-07-13 11:21:16',734,'text/markdown',_binary 'UUUUUUUUUUUUUUUU',_binary '\ãx{‡nI>®\Í[\ç\ñ*[','e7d4513b21e42297ee2e1a54c537919370968d70f9d1fd268574daa32d9afeb3',1,_binary 'P\åqgbL\n¡W\Ù77²¬','../Uploads/storage/3166ded9-ff67-4f31-977d-4c53661ee289-1.cloud','active',NULL),('reset_jetbrains_eval_windows.vbs','2026-07-13 15:37:21',1019,'application/octet-stream',_binary 'UUUUUUUUUUUUUUUU',_binary '\ã“7È®\êC»¤¦\Ø\"\İy','5e3da17d0d819f4441ca6facaab51bddbb56bfb39d99d7b7d7c802c5d6266f22',1,_binary '£\óB)<JvŒÊ¹qƒ\ì','../Uploads/storage/675aa0c5-e1cf-4789-9d8a-8434a123ad2d-1.cloud','active',NULL),('VerificationSendRequest.md','2026-07-13 11:20:45',917,'text/markdown',_binary 'UUUUUUUUUUUUUUUU',_binary '\äG9^\÷OÊº½š‰\0\èz','c5b4b7b174866fe0f864afb83b19ed9fd16c70913eba7eb3ed44ca9fa2a3e975',1,_binary 'P\åqgbL\n¡W\Ù77²¬','../Uploads/storage/bd68c2b0-d88d-424d-a5ab-29bde4ca1edd-1.cloud','active',NULL),('background-1.jpg','2026-07-05 10:39:13',503539,'image/jpeg',_binary 'UUUUUUUUUUUUUUUU',_binary '\äøs‰\Ï\ÇB½\ã\Ü\è$b\î\ñ','be870166bacd8cb5ec99117f9c99201590530b16dbc57a1ba5cbcdc0e41a0565',1,_binary 'ù0¢\\@`¤\Ã\nœ\ë\"','../Uploads/storage/7a9f5937-d387-4857-9bb3-29ed37fe7537-1.cloud','active',NULL),('CarClientSocketSystem.cpython-310.pyc','2026-07-07 11:12:16',2318,'application/octet-stream',_binary 'UUUUUUUUUUUUUUUU',_binary '\å^\Zt$O–”oŒˆ¼Ÿ¥','39f2a90f0ddc18de399c84122a3fc29ed1a4038ae76b73861763ce7a2f3a7025',1,_binary 'Øƒ\ÒY\r¼JS¼°\0`¯$','../Uploads/storage/d4214426-c81f-4367-8b11-12ce570dab8c-1.cloud','active',NULL),('power.jar','2026-07-13 15:37:11',9222,'application/java-archive',_binary 'UUUUUUUUUUUUUUUU',_binary '\æ\rXm$G\î¦\é>YS\n','7819e5b968ce5ea2e638e53d84089d35e89e9ea3088f18f8dbf6dd38d14ab25a',1,_binary '\ã•°Ö¢O\óƒ\\F._.','../Uploads/storage/14f4ed26-c670-4d18-887a-576a9a8ebb38-1.cloud','active',NULL),('é™„ä»¶2ï¼š2025å¹´ç§‹å­£å­¦æœŸæœŸæœ«è€ƒè¯•å®‰æ’è¡¨ï¼ˆæ•°å­¦ä¸å¤§æ•°æ®å­¦é™¢ï¼‰ - - å¤§å­¦ç‰©ç†A.xlsx','2026-07-06 10:39:42',22693,'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',_binary 'UUUUUUUUUUUUUUUU',_binary '\æøi´\Ñ@L¤|QÙ’/\â\Ç','979b8e74a42d9329a579bea3d176f68620e742969c5ba6ff8d7fec0e31ba8d80',1,_binary '\nh¯H\ÚMĞŸ·3Û >«','../Uploads/storage/5987bc63-c503-4f29-bc77-203f62b48864-1.cloud','active',NULL),('reset_jetbrains_eval_mac_linux.sh','2026-07-13 15:37:24',558,'text/x-sh',_binary 'UUUUUUUUUUUUUUUU',_binary '\æŒì½¢IKq±D±^\Ä2©','1a34b0606276623da4fcc89f094068d74475e7c2e5d92b16cdbc714df3a0eb6e',1,_binary '£\óB)<JvŒÊ¹qƒ\ì','../Uploads/storage/6e819583-b734-4002-9703-394a4d4be8af-1.cloud','active',NULL),('UploadsSessionInternalVO.md','2026-07-13 11:20:34',1446,'text/markdown',_binary 'UUUUUUUUUUUUUUUU',_binary '\æ°`;ŸN×¶YˆÅ»m\Ù','03fddf6e72d55c5833bfd310165a07d374707a83f4d15b29f155a1a3c9bf3e0b',1,_binary 'P\åqgbL\n¡W\Ù77²¬','../Uploads/storage/f4a5635a-7131-44d8-bc35-62210cbff6e3-1.cloud','active',NULL),('devecostudio.vmoptions','2026-07-05 14:09:05',768,'application/octet-stream',_binary 'UUUUUUUUUUUUUUUU',_binary '\ç\nzü\âÁGn†\ô`tW†\Í ','c2558bc8d5b4af8a26ca8460bff126d7738c2b8780d43d849baa58f0ce54f1c6',1,_binary '­\\Œ\ñ;N\á\É$TĞ \Z','../Uploads/storage/8bc50c6b-725a-4070-a0fd-c0939b45ee69-1.cloud','active',NULL),('gps.py','2026-07-07 11:12:01',449,'text/x-python-script',_binary 'UUUUUUUUUUUUUUUU',_binary '\çpÅ‡S‡IÀ¹N5F\0¬Áj','af2a19bcbe6518c361e4ca5496a71892db7027a7276cfb1af4f7b9bfefee5f3c',1,_binary 'ªªªªªªªªªªªªªªª¥','../Uploads/storage/2ab7b53f-28b4-4282-90d9-a34bd9639a8c-1.cloud','active',NULL),('devecostudio.vmoptions','2026-07-13 15:36:42',634,'application/octet-stream',_binary 'UUUUUUUUUUUUUUUU',_binary '\ê^»’5\ßDÙ„\èş\Ñ\Í\Ï\ç·','c03bf5ae617eae087cf21a339dd956ce1260d11863e60a21b8b0365f4afdc7d5',1,_binary '‰º\Ë\÷.\ÄJ¡•uY£\É\ñª/','../Uploads/storage/78da0025-2309-489f-8ba8-02e6f01eade7-1.cloud','active',NULL),('å®¶åº­é‡‡è´­æ¸…å•_2026-2-4.txt','2026-07-06 19:32:31',622,'text/plain',_binary 'UUUUUUUUUUUUUUUU',_binary '\ê\Ì\à$ÿ`GŸ0!\Å\\»f]','ea21a9442b83e326417ea3f23bf500b822528143e63db0264b638f861cfac89e',1,_binary 'ªªªªªªªªªªªªªªª¥','../Uploads/storage/039b68cc-af0a-4e6c-910d-58e8d809a37d-1.cloud','active',NULL),('UserProfileVO.md','2026-07-13 11:21:19',871,'text/markdown',_binary 'UUUUUUUUUUUUUUUU',_binary '\ê\ß&\è¼nMÕ¸üp\"$«V','5dfc15f362439fd9c534bf1eb5e96c20a6e8cb7a6693dac1e7f9b77d4c5e4924',1,_binary 'P\åqgbL\n¡W\Ù77²¬','../Uploads/storage/b61aaed0-3a0f-4547-90ea-0a8ebfb959ae-1.cloud','active',NULL),('JsonResultListShareLinkVO.md','2026-07-13 11:20:16',704,'text/markdown',_binary 'UUUUUUUUUUUUUUUU',_binary '\ì„\çy\ÍIp¹}\Øgß´9\Ä','e2f41e38d4f225add25e1db7e0cb038c24a919de1bee7f8ad821f42d8c980701',1,_binary 'P\åqgbL\n¡W\Ù77²¬','../Uploads/storage/c9836c85-f9ff-443e-a2d8-008033b52da0-1.cloud','active',NULL),('url.conf','2026-07-13 15:35:43',74,'application/octet-stream',_binary 'UUUUUUUUUUUUUUUU',_binary '\í \×E¿\ÃN—42:Á¥2','88e1dca8019ad412cf2c6fbd947a83786cffc7b32f1ee35594d25d1f38fae5f8',1,_binary 'M7™Œ\ßZF×½+\Èé®»“	','../Uploads/storage/8567834a-6a96-4fba-a4a0-3c3c3c6e8a0d-1.cloud','active',NULL),('configuration.ts','2026-07-13 11:19:09',4036,'application/octet-stream',_binary 'UUUUUUUUUUUUUUUU',_binary '\ğ\õo‰QE“®./\n\Ì_','25bd0493d76e388c46dfb0624c93f97806f861add7a2d6c7b5ac862585502a65',1,_binary 'ªªªªªªªªªªªªªªª¥','../Uploads/storage/142b64f7-99e5-47c5-86a5-5995dfa50645-1.cloud','active',NULL),('uninstall-all-users.vbs','2026-07-13 15:36:04',1065,'application/octet-stream',_binary 'UUUUUUUUUUUUUUUU',_binary '\ñ\0B>\Ì\ÆF#^Z”\ÌNn','11811f0c25f30336a0c835dad7e30e7c9810392d207540c847da0e1b7c06ce72',1,_binary 'iJr\áD¼²‰¥\Ë\ãW²','../Uploads/storage/8b26e5ce-e737-4762-a1f5-06b2db3a8dd6-1.cloud','active',NULL),('CarClientSocketSystem.py','2026-07-07 11:12:05',2313,'text/x-python-script',_binary 'UUUUUUUUUUUUUUUU',_binary '\òo5\âZ¦L\ğ«2×¥\ìı\Ó','18ef45dc19f1792e73c4611884e316b0d221841ad6e9651245f5cf92e2256c56',1,_binary 't…¯»eµK\"¨\ÍZ›‚•¬','../Uploads/storage/f97c81fb-f100-465f-b9e2-bd46cd511b92-1.cloud','active',NULL),('uninstall-current-user.vbs','2026-07-13 15:36:09',749,'application/octet-stream',_binary 'UUUUUUUUUUUUUUUU',_binary '\òş„¬k+FÔ³q\ñ»ş\ğez','2f9a8e832664bacd9ca9bd3504a0df4e8b6abce9fa153f22c0bbf8192d114fb6',1,_binary 'iJr\áD¼²‰¥\Ë\ãW²','../Uploads/storage/bceda74d-0361-499f-979d-b029c5373fc5-1.cloud','active',NULL),('JsonResultListFolderFileInfo.md','2026-07-13 11:19:24',719,'text/markdown',_binary 'UUUUUUUUUUUUUUUU',_binary '\óH7ß¤Fª‰¿$½¥ˆ\è¢','b2457dcbd5ee38c3c736b231705797a11ea87372231d6c6ec57cd421822e27b4',1,_binary 'P\åqgbL\n¡W\Ù77²¬','../Uploads/storage/a86477f9-9d32-4989-9891-bf8edcf19451-1.cloud','active',NULL),('.npmignore','2026-07-13 11:18:46',94,'application/octet-stream',_binary 'UUUUUUUUUUUUUUUU',_binary '\ó›RmRGJ¬kÆ®f&\æ&','36d5b2d5697ab17b5b06f729ad1382ee5b9a4ea9755e977b763ef7da285d090e',1,_binary 'ªªªªªªªªªªªªªªª¥','../Uploads/storage/3f520c77-238d-4292-9fca-f90f19c4d2fc-1.cloud','active',NULL),('rubymine.vmoptions','2026-07-05 14:08:58',702,'application/octet-stream',_binary 'UUUUUUUUUUUUUUUU',_binary '\õZŠ\å›J Ÿ2.f%\î\Ê?','84a349d678b98359cc6fb13dd63c0cf3b790bce13f6f2b2284726b70386b13f6',1,_binary '­\\Œ\ñ;N\á\É$TĞ \Z','../Uploads/storage/2f4681ea-04f6-483f-9f1c-776bbd096cb6-1.cloud','active',NULL),('NodeControllerApi.md','2026-07-13 11:21:05',19343,'text/markdown',_binary 'UUUUUUUUUUUUUUUU',_binary '\÷”\ê7©L\ã™ş\ğ¡\Èg\Ø','ef8ef90b750356c29874fa6d0cd54f7cb5bebac417f15e5d5cfa61dea826c89d',1,_binary 'P\åqgbL\n¡W\Ù77²¬','../Uploads/storage/3fcc07d9-ca5c-4bdb-ae10-d599b79d3929-1.cloud','active',NULL),('JsonResultBoolean.md','2026-07-13 11:20:43',645,'text/markdown',_binary 'UUUUUUUUUUUUUUUU',_binary '\÷\ô\Ï\0l²Mo-\ì?d\Ê\İ','6b668e4ad2a6c547f3e8705264a25c578cf5215aca53c38772a46db24469eb53',1,_binary 'P\åqgbL\n¡W\Ù77²¬','../Uploads/storage/389de0e2-a934-49ec-afc1-908dce0aee6f-1.cloud','active',NULL),('RenameNodeRequest.md','2026-07-13 11:21:38',503,'text/markdown',_binary 'UUUUUUUUUUUUUUUU',_binary 'ùy?3\äG/–«\ğ‘Æ­\Ü','5463c598a427709f3faa86463574bf96803be0411f67686096f97644a94e7584',1,_binary 'P\åqgbL\n¡W\Ù77²¬','../Uploads/storage/a002fff3-f1ff-4f79-a835-545856181cef-1.cloud','active',NULL),('ç­¾åˆ°è¡¨-å¤§å­¦è‹±è¯­Bï¼ˆ1ï¼‰å®¶åº­.xls','2026-07-06 10:39:15',33280,'application/vnd.ms-excel',_binary 'UUUUUUUUUUUUUUUU',_binary 'ú\âù\â\éÿGU‡,R\êa\á‘','06a4569f4998692e1bd4f85d33415795e4a9bf77f2bafc0fffc5b756037059e6',1,_binary '\Z\ëd\ÕR\ZA¿³U½Ö­‹¢','../Uploads/storage/0a5c7c48-afc4-4527-9857-2cda14eed857-1.cloud','active',NULL),('è€ƒåœºè€ƒè¯•å®‰æ’ï¼ˆè€ƒç”Ÿç­¾åˆ°è¡¨ï¼‰ä¹ è¿‘å¹³æ–°æ—¶ä»£ä¸­å›½ç‰¹è‰²ç¤¾ä¼šä¸»ä¹‰æ€æƒ³æ¦‚è®º.xls','2026-07-06 10:39:31',233984,'application/vnd.ms-excel',_binary 'UUUUUUUUUUUUUUUU',_binary 'ûl\ö‡\r#AA¡5*¢\È{£ø','b956eb0cd1b6fbf965ec2dd25e3e4912145b4a11c32bf675e82ef00b028cb9d5',1,_binary '³¢\ÂAx\ôAG\öˆœ','../Uploads/storage/da206101-a78b-4960-8256-04c33bedc39e-1.cloud','active',NULL),('å¤§å­¦è‹±è¯­Bï¼ˆ1ï¼‰äººå·¥ç­¾åˆ°è¡¨ (1).xls','2026-07-06 10:39:22',93696,'application/vnd.ms-excel',_binary 'UUUUUUUUUUUUUUUU',_binary 'û\ö\ê5°A\ô°+ü£Aèª´','35a0c21fd523f11f7d052c43df87760045cb6670a94181ce0dd2c670880f5d8a',1,_binary '\Z\ëd\ÕR\ZA¿³U½Ö­‹¢','../Uploads/storage/27d8fa97-6a21-400c-b5ec-bfa8c155dd2a-1.cloud','active',NULL),('é™„ä»¶2ï¼š2025å¹´ç§‹å­£å­¦æœŸæœŸæœ«è€ƒè¯•å®‰æ’è¡¨ï¼ˆæ•°å­¦ä¸å¤§æ•°æ®å­¦é™¢ï¼‰ - - é«˜ç­‰æ•°å­¦B1.xlsx','2026-07-06 10:39:51',49729,'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',_binary 'UUUUUUUUUUUUUUUU',_binary 'ür,e\ã™J\ã¢0W\çŸE','1dc9fcccd7bf707c82e85faaec40d9bceaadc6e879c9ac36f03e2c3115e4edb1',1,_binary '\nh¯H\ÚMĞŸ·3Û >«','../Uploads/storage/76e3b7d6-daaa-4977-ab51-2da0644e49f1-1.cloud','active',NULL),('.DS_Store','2026-07-13 15:35:15',6148,'application/octet-stream',_binary 'UUUUUUUUUUUUUUUU',_binary 'ınˆ\İKB“»R²yg','c7c504096805b7343cf3458013a0329225eecd5b4c8a257df568e7874d815ce4',1,_binary 'n$ûOœB&‰©°c','../Uploads/storage/b3551fc2-5138-4cb4-9e89-65238f4c308f-1.cloud','active',NULL),('tsconfig.json','2026-07-13 11:19:04',321,'application/json',_binary 'UUUUUUUUUUUUUUUU',_binary 'ş\Èt]\Ç@Ï»F=\Å\Û\ôJ','62ba9ee42b3d6dcbb61be713ed46a630f37937d8fe97d587414173bf8a8fb610',1,_binary 'ªªªªªªªªªªªªªªª¥','../Uploads/storage/237dd121-bfaf-4f51-bfef-4f9acc4b5e8b-1.cloud','active',NULL),('3.py','2026-07-07 11:12:42',243,'text/x-python-script',_binary 'UUUUUUUUUUUUUUUU',_binary 'ş!µJ\É@û¶Tn!$\Ø\Ø','90518ee82c21635a5fdf46bc531ff6703bdbf808eef9ee774f616ec650eab46e',1,_binary 'ªªªªªªªªªªªªªªª¥','../Uploads/storage/67463396-73eb-4c72-8d0c-7ced4d79a490-1.cloud','active',NULL),('FileSearchVO.md','2026-07-13 11:21:54',799,'text/markdown',_binary 'UUUUUUUUUUUUUUUU',_binary 'ÿÚº\ô´RJKƒ\åsEQ@ ','6de88c9521b9b39ea50393639a3cc30edfad152777799ae394cee97cfb0bbe20',1,_binary 'P\åqgbL\n¡W\Ù77²¬','../Uploads/storage/985130b7-2e54-4f82-beaa-fd70e6628c70-1.cloud','active',NULL);
/*!40000 ALTER TABLE `pcd_file_info_table` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `pcd_file_star_table`
--

DROP TABLE IF EXISTS `pcd_file_star_table`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `pcd_file_star_table` (
  `star_id` bigint NOT NULL AUTO_INCREMENT,
  `star_user_id` binary(16) NOT NULL COMMENT 'ç”¨æˆ·ID',
  `star_target_type` enum('file','folder') NOT NULL DEFAULT 'file' COMMENT 'æ”¶è—ç›®æ ‡ç±»å‹ï¼šfile=æ–‡ä»¶, folder=æ–‡ä»¶å¤¹',
  `star_file_id` binary(16) DEFAULT NULL COMMENT 'æ–‡ä»¶IDï¼ˆæ”¶è—æ–‡ä»¶æ—¶å¡«å†™ï¼‰',
  `star_node_id` binary(16) DEFAULT NULL COMMENT 'æ–‡ä»¶å¤¹èŠ‚ç‚¹IDï¼ˆæ”¶è—æ–‡ä»¶å¤¹æ—¶å¡«å†™ï¼‰',
  `star_starred_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'æ”¶è—æ—¶é—´',
  PRIMARY KEY (`star_id`),
  UNIQUE KEY `uk_user_file_star` (`star_user_id`,`star_file_id`),
  UNIQUE KEY `uk_user_folder_star` (`star_user_id`,`star_node_id`),
  KEY `star_file_id` (`star_file_id`),
  KEY `star_node_id` (`star_node_id`),
  KEY `idx_user_starred` (`star_user_id`,`star_starred_at`),
  CONSTRAINT `pcd_file_star_table_ibfk_1` FOREIGN KEY (`star_user_id`) REFERENCES `pcd_user_info_table` (`user_id`) ON DELETE CASCADE,
  CONSTRAINT `pcd_file_star_table_ibfk_2` FOREIGN KEY (`star_file_id`) REFERENCES `pcd_file_info_table` (`file_id`) ON DELETE CASCADE,
  CONSTRAINT `pcd_file_star_table_ibfk_3` FOREIGN KEY (`star_node_id`) REFERENCES `pcd_directory_tree_table` (`node_id`) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=35 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='æ–‡ä»¶/æ–‡ä»¶å¤¹æ”¶è—è¡¨';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `pcd_file_star_table`
--

LOCK TABLES `pcd_file_star_table` WRITE;
/*!40000 ALTER TABLE `pcd_file_star_table` DISABLE KEYS */;
INSERT INTO `pcd_file_star_table` VALUES (6,_binary 'UUUUUUUUUUUUUUUU','file',_binary '\\L<¤NÉ‚’\ÈwI“Y',NULL,'2026-07-05 22:18:03'),(8,_binary 'UUUUUUUUUUUUUUUU','file',_binary '.@¬I­I´¥\ÌG;Fd‰\Ã',NULL,'2026-07-05 22:20:09'),(9,_binary 'UUUUUUUUUUUUUUUU','file',_binary '\Ä\Æ]?gBÜ¯ÍŸœm\Ù|',NULL,'2026-07-05 22:20:10'),(12,_binary 'UUUUUUUUUUUUUUUU','file',_binary '5¸E\Ö\ÏeC®„\Æp­\Ó5Çˆ',NULL,'2026-07-13 19:11:19'),(13,_binary 'UUUUUUUUUUUUUUUU','file',_binary '?hE§q»GÊ£°\Öı\0V·',NULL,'2026-07-13 19:11:23'),(14,_binary 'UUUUUUUUUUUUUUUU','file',_binary 'x˜f\Ü\Å4O%´\Ğ\æ\Ü\çb\Ô\æ',NULL,'2026-07-13 19:11:25'),(15,_binary 'UUUUUUUUUUUUUUUU','file',_binary 'tµ ¬b\ô@}qh€„9tG',NULL,'2026-07-13 19:11:26'),(16,_binary 'UUUUUUUUUUUUUUUU','file',_binary '˜¦¹¬plL„„ymûÀ\ë_€',NULL,'2026-07-13 19:11:29'),(17,_binary 'UUUUUUUUUUUUUUUU','file',_binary '\Ô\Ü\Ë\ëˆeEº½¸rrûz',NULL,'2026-07-13 19:11:31'),(19,_binary 'UUUUUUUUUUUUUUUU','file',_binary 'oÊ»z\ÜEÏ¾¿‹\n5Ù°',NULL,'2026-07-13 23:22:18'),(20,_binary 'UUUUUUUUUUUUUUUU','file',_binary 'Qd\ñÁ™IƒNÚº\÷wV',NULL,'2026-07-13 23:22:19'),(21,_binary 'UUUUUUUUUUUUUUUU','file',_binary 'F\çk\ñªZFH±oªF;…‰',NULL,'2026-07-13 23:22:20'),(22,_binary 'UUUUUUUUUUUUUUUU','file',_binary '\ÊX¯\ò@‹ƒ0\Ù\å\Ê\Ø',NULL,'2026-07-13 23:22:23'),(23,_binary 'UUUUUUUUUUUUUUUU','folder',NULL,_binary '­\ß\ÕkzHŒ\á\õ\ô/»:¾','2026-07-13 23:22:25'),(24,_binary 'UUUUUUUUUUUUUUUU','file',_binary '/i$)\ìƒN™¬ù\ß#­U³',NULL,'2026-07-13 23:22:33'),(25,_binary 'UUUUUUUUUUUUUUUU','file',_binary '%s\Û\'0§OÍ¹X‡›z„û',NULL,'2026-07-13 23:28:58'),(26,_binary 'UUUUUUUUUUUUUUUU','file',_binary '\0\'M\óI“´­Æ§UE',NULL,'2026-07-13 23:29:01'),(27,_binary 'UUUUUUUUUUUUUUUU','file',_binary '·F\Èl­BÜ—¨k\ÍA\Ì!',NULL,'2026-07-13 23:29:07'),(29,_binary 'UUUUUUUUUUUUUUUU','folder',NULL,_binary '×’Aİ»\ñ@‰ic±&2N','2026-07-15 04:45:37'),(30,_binary 'UUUUUUUUUUUUUUUU','folder',NULL,_binary '1\ÜfCJ>§jO‡µ\Ë©','2026-07-15 04:46:24'),(31,_binary 'UUUUUUUUUUUUUUUU','file',_binary '\Ü#\ô¤?\òA{±PF!†ƒm',NULL,'2026-07-15 04:55:59'),(32,_binary 'UUUUUUUUUUUUUUUU','file',_binary '´!\Ø4\ØG²ƒÅ‚q·',NULL,'2026-07-15 04:56:17'),(33,_binary 'UUUUUUUUUUUUUUUU','folder',NULL,_binary '\Ş\ïs.üLÿ´%—\å uG','2026-07-16 02:28:50'),(34,_binary 'UUUUUUUUUUUUUUUU','folder',NULL,_binary 'P\åqgbL\n¡W\Ù77²¬','2026-07-16 02:29:10');
/*!40000 ALTER TABLE `pcd_file_star_table` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `pcd_file_tag_table`
--

DROP TABLE IF EXISTS `pcd_file_tag_table`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `pcd_file_tag_table` (
  `ft_id` bigint NOT NULL AUTO_INCREMENT COMMENT 'å…³è”ID',
  `ft_user_id` binary(16) NOT NULL COMMENT 'ç”¨æˆ·IDï¼ˆå†—ä½™ï¼ŒåŠ é€ŸæŸ¥è¯¢ï¼‰',
  `ft_tag_id` bigint NOT NULL COMMENT 'æ ‡ç­¾ID',
  `ft_target_type` enum('file','folder') NOT NULL COMMENT 'ç›®æ ‡ç±»å‹',
  `ft_file_id` binary(16) DEFAULT NULL COMMENT 'æ–‡ä»¶IDï¼ˆtarget_type=fileæ—¶ï¼‰',
  `ft_node_id` binary(16) DEFAULT NULL COMMENT 'æ–‡ä»¶å¤¹èŠ‚ç‚¹IDï¼ˆtarget_type=folderæ—¶ï¼‰',
  `ft_tagged_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'æ‰“æ ‡ç­¾æ—¶é—´',
  PRIMARY KEY (`ft_id`),
  UNIQUE KEY `uk_file_tag` (`ft_user_id`,`ft_tag_id`,`ft_target_type`,`ft_file_id`,`ft_node_id`),
  KEY `idx_tag_file` (`ft_tag_id`),
  KEY `idx_file_tag` (`ft_file_id`),
  KEY `idx_node_tag` (`ft_node_id`),
  KEY `idx_user_tag` (`ft_user_id`,`ft_tag_id`),
  CONSTRAINT `pcd_file_tag_table_ibfk_1` FOREIGN KEY (`ft_user_id`) REFERENCES `pcd_user_info_table` (`user_id`) ON DELETE CASCADE,
  CONSTRAINT `pcd_file_tag_table_ibfk_2` FOREIGN KEY (`ft_tag_id`) REFERENCES `pcd_tag_table` (`tag_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='æ–‡ä»¶æ ‡ç­¾å…³è”è¡¨';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `pcd_file_tag_table`
--

LOCK TABLES `pcd_file_tag_table` WRITE;
/*!40000 ALTER TABLE `pcd_file_tag_table` DISABLE KEYS */;
/*!40000 ALTER TABLE `pcd_file_tag_table` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `pcd_im_call_record`
--

DROP TABLE IF EXISTS `pcd_im_call_record`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `pcd_im_call_record` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'ä¸»é”® ID',
  `call_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'é€šè¯å”¯ä¸€ ID',
  `room_id` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'é€šè¯æˆ¿é—´ IDï¼ˆç¾¤ç»„é€šè¯æ—¶ä½¿ç”¨ï¼‰',
  `call_type` tinyint NOT NULL DEFAULT '2' COMMENT 'é€šè¯ç±»å‹ï¼š1-è¯­éŸ³é€šè¯ 2-è§†é¢‘é€šè¯',
  `call_mode` tinyint NOT NULL DEFAULT '1' COMMENT 'é€šè¯æ¨¡å¼ï¼š1-P2P 2-ç¾¤ç»„',
  `caller_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'å‘èµ·è€…ç”¨æˆ· ID',
  `callee_id` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'è¢«å«è€…ç”¨æˆ· IDï¼ˆP2P æ¨¡å¼ï¼‰',
  `status` tinyint NOT NULL DEFAULT '0' COMMENT 'é€šè¯çŠ¶æ€ï¼š0-ç­‰å¾…æ¥å¬ 1-é€šè¯ä¸­ 2-å·²æ‹’ç» 3-å·²å–æ¶ˆ 4-å·²æŒ‚æ–­ 5-è¶…æ—¶ 6-å¿™çº¿',
  `start_time` datetime DEFAULT NULL COMMENT 'é€šè¯å¼€å§‹æ—¶é—´',
  `end_time` datetime DEFAULT NULL COMMENT 'é€šè¯ç»“æŸæ—¶é—´',
  `duration` bigint DEFAULT '0' COMMENT 'é€šè¯æŒç»­æ—¶é—´ï¼ˆç§’ï¼‰',
  `reject_reason` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'æ‹’ç»åŸå› ',
  `participants` json DEFAULT NULL COMMENT 'å‚ä¸è€…åˆ—è¡¨ï¼ˆJSON æ•°ç»„ï¼‰',
  `video_enabled` tinyint(1) DEFAULT '1' COMMENT 'æ˜¯å¦å¯ç”¨è§†é¢‘',
  `screen_share_enabled` tinyint(1) DEFAULT '0' COMMENT 'æ˜¯å¦å¯ç”¨å±å¹•å…±äº«',
  `hangup_by` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'æŒ‚æ–­æ–¹ç”¨æˆ· ID',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'åˆ›å»ºæ—¶é—´',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'æ›´æ–°æ—¶é—´',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_call_id` (`call_id`),
  KEY `idx_caller_id` (`caller_id`),
  KEY `idx_callee_id` (`callee_id`),
  KEY `idx_create_time` (`create_time`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='IM é€šè¯è®°å½•è¡¨';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `pcd_im_call_record`
--

LOCK TABLES `pcd_im_call_record` WRITE;
/*!40000 ALTER TABLE `pcd_im_call_record` DISABLE KEYS */;
/*!40000 ALTER TABLE `pcd_im_call_record` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `pcd_im_conversation`
--

DROP TABLE IF EXISTS `pcd_im_conversation`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `pcd_im_conversation` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'ä¸»é”® ID',
  `conversation_id` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'ä¼šè¯å”¯ä¸€ ID',
  `conversation_type` tinyint NOT NULL DEFAULT '1' COMMENT 'ä¼šè¯ç±»å‹ï¼š1-å•èŠ 2-ç¾¤èŠ',
  `user_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'å½“å‰ç”¨æˆ· ID',
  `target_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'å¯¹æ–¹ IDï¼ˆå•èŠä¸ºå¯¹æ–¹ userIdï¼Œç¾¤èŠä¸º groupIdï¼‰',
  `last_message` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'æœ€åä¸€æ¡æ¶ˆæ¯å†…å®¹',
  `last_message_type` tinyint DEFAULT NULL COMMENT 'æœ€åä¸€æ¡æ¶ˆæ¯ç±»å‹',
  `last_message_time` datetime DEFAULT NULL COMMENT 'æœ€åä¸€æ¡æ¶ˆæ¯æ—¶é—´',
  `unread_count` int NOT NULL DEFAULT '0' COMMENT 'æœªè¯»æ¶ˆæ¯æ•°',
  `is_top` tinyint(1) NOT NULL DEFAULT '0' COMMENT 'æ˜¯å¦ç½®é¡¶ï¼š0-å¦ 1-æ˜¯',
  `is_muted` tinyint(1) NOT NULL DEFAULT '0' COMMENT 'æ˜¯å¦å…æ‰“æ‰°ï¼š0-å¦ 1-æ˜¯',
  `status` tinyint NOT NULL DEFAULT '0' COMMENT 'çŠ¶æ€ï¼š0-æ­£å¸¸ 1-å·²åˆ é™¤',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'åˆ›å»ºæ—¶é—´',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'æ›´æ–°æ—¶é—´',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_conversation_id` (`conversation_id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_user_target` (`user_id`,`target_id`,`conversation_type`),
  KEY `idx_last_message_time` (`last_message_time`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='IM ä¼šè¯è¡¨';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `pcd_im_conversation`
--

LOCK TABLES `pcd_im_conversation` WRITE;
/*!40000 ALTER TABLE `pcd_im_conversation` DISABLE KEYS */;
INSERT INTO `pcd_im_conversation` VALUES (1,'88',8,'a','88','',0,'2026-07-16 02:00:08',1,0,0,0,'2026-07-16 01:58:45','2026-07-16 02:00:07'),(2,'880',8,'a','880',NULL,NULL,NULL,0,0,0,0,'2026-07-16 01:59:18','2026-07-16 01:59:18');
/*!40000 ALTER TABLE `pcd_im_conversation` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `pcd_im_group`
--

DROP TABLE IF EXISTS `pcd_im_group`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `pcd_im_group` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'ä¸»é”® ID',
  `group_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'ç¾¤ç»„å”¯ä¸€ IDï¼ˆé›ªèŠ±ç®—æ³•ï¼‰',
  `group_name` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'ç¾¤ç»„åç§°',
  `avatar` varchar(512) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'ç¾¤ç»„å¤´åƒ URL',
  `owner_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'ç¾¤ä¸»ç”¨æˆ· ID',
  `announcement` text COLLATE utf8mb4_unicode_ci COMMENT 'ç¾¤å…¬å‘Š',
  `description` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'ç¾¤ç®€ä»‹',
  `member_count` int NOT NULL DEFAULT '0' COMMENT 'å½“å‰æˆå‘˜æ•°',
  `max_members` int NOT NULL DEFAULT '500' COMMENT 'æœ€å¤§æˆå‘˜æ•°',
  `join_mode` tinyint NOT NULL DEFAULT '0' COMMENT 'åŠ ç¾¤æ–¹å¼ï¼š0-è‡ªç”±åŠ å…¥ 1-éœ€è¦å®¡æ ¸ 2-ç¦æ­¢åŠ å…¥',
  `is_all_muted` tinyint(1) NOT NULL DEFAULT '0' COMMENT 'æ˜¯å¦å…¨å‘˜ç¦è¨€ï¼š0-å¦ 1-æ˜¯',
  `status` tinyint NOT NULL DEFAULT '0' COMMENT 'çŠ¶æ€ï¼š0-æ­£å¸¸ 1-å·²è§£æ•£',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'åˆ›å»ºæ—¶é—´',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'æ›´æ–°æ—¶é—´',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_group_id` (`group_id`),
  KEY `idx_owner_id` (`owner_id`),
  KEY `idx_group_name` (`group_name`)
) ENGINE=InnoDB AUTO_INCREMENT=7 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='IM ç¾¤ç»„è¡¨';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `pcd_im_group`
--

LOCK TABLES `pcd_im_group` WRITE;
/*!40000 ALTER TABLE `pcd_im_group` DISABLE KEYS */;
INSERT INTO `pcd_im_group` VALUES (1,'335820910080692224','æµ‹è¯•ç¾¤èŠ',NULL,'55555555-5555-5555-5555-555555555555',NULL,NULL,1,500,0,0,0,'2026-07-16 00:32:24','2026-07-16 00:32:24'),(2,'335820910412042240','æµ‹è¯•ç¾¤èŠ',NULL,'55555555-5555-5555-5555-555555555555',NULL,NULL,1,500,0,0,0,'2026-07-16 00:32:24','2026-07-16 00:32:24'),(3,'335823676501004288','æµ‹è¯•ç¾¤èŠ4',NULL,'55555555-5555-5555-5555-555555555555',NULL,NULL,1,500,0,0,0,'2026-07-16 00:43:24','2026-07-16 00:43:24'),(4,'335824088335519744','33',NULL,'55555555-5555-5555-5555-555555555555',NULL,NULL,1,500,0,0,0,'2026-07-16 00:45:02','2026-07-16 00:45:02'),(5,'335824933898817536','335',NULL,'55555555-5555-5555-5555-555555555555',NULL,NULL,1,500,0,0,0,'2026-07-16 00:48:24','2026-07-16 00:48:24'),(6,'335840827223969792','888',NULL,'a',NULL,NULL,2,500,0,0,1,'2026-07-16 01:51:33','2026-07-16 01:53:34');
/*!40000 ALTER TABLE `pcd_im_group` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `pcd_im_group_member`
--

DROP TABLE IF EXISTS `pcd_im_group_member`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `pcd_im_group_member` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'ä¸»é”® ID',
  `group_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'ç¾¤ç»„ ID',
  `user_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'ç”¨æˆ· ID',
  `role` tinyint NOT NULL DEFAULT '3' COMMENT 'ç¾¤å†…è§’è‰²ï¼š1-ç¾¤ä¸» 2-ç®¡ç†å‘˜ 3-æˆå‘˜',
  `alias` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'ç¾¤å†…åˆ«å',
  `mute_until` datetime DEFAULT NULL COMMENT 'ç¦è¨€æˆªæ­¢æ—¶é—´',
  `last_read_seq` bigint NOT NULL DEFAULT '0' COMMENT 'æœ€åé˜…è¯»çš„æ¶ˆæ¯åºå·',
  `join_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'åŠ å…¥æ—¶é—´',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'åˆ›å»ºæ—¶é—´',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_group_user` (`group_id`,`user_id`),
  KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB AUTO_INCREMENT=8 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='IM ç¾¤ç»„æˆå‘˜è¡¨';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `pcd_im_group_member`
--

LOCK TABLES `pcd_im_group_member` WRITE;
/*!40000 ALTER TABLE `pcd_im_group_member` DISABLE KEYS */;
INSERT INTO `pcd_im_group_member` VALUES (1,'335820910412042240','55555555-5555-5555-5555-555555555555',1,NULL,NULL,0,'2026-07-16 00:32:24','2026-07-16 00:32:24'),(2,'335820910080692224','55555555-5555-5555-5555-555555555555',1,NULL,NULL,0,'2026-07-16 00:32:24','2026-07-16 00:32:24'),(3,'335823676501004288','55555555-5555-5555-5555-555555555555',1,NULL,NULL,0,'2026-07-16 00:43:24','2026-07-16 00:43:24'),(4,'335824088335519744','55555555-5555-5555-5555-555555555555',1,NULL,NULL,0,'2026-07-16 00:45:02','2026-07-16 00:45:02'),(5,'335824933898817536','55555555-5555-5555-5555-555555555555',1,NULL,NULL,0,'2026-07-16 00:48:24','2026-07-16 00:48:24'),(6,'335840827223969792','a',1,NULL,NULL,0,'2026-07-16 01:51:33','2026-07-16 01:51:33'),(7,'335840827223969792','88',3,NULL,'2026-07-16 03:35:27',0,'2026-07-16 01:52:05','2026-07-16 01:52:05');
/*!40000 ALTER TABLE `pcd_im_group_member` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `pcd_im_message`
--

DROP TABLE IF EXISTS `pcd_im_message`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `pcd_im_message` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'ä¸»é”® ID',
  `message_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'æ¶ˆæ¯å”¯ä¸€ IDï¼ˆé›ªèŠ±ç®—æ³•ï¼‰',
  `conversation_id` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'ä¼šè¯ ID',
  `conversation_type` tinyint NOT NULL DEFAULT '1' COMMENT 'ä¼šè¯ç±»å‹ï¼š1-å•èŠ 2-ç¾¤èŠ',
  `message_type` tinyint NOT NULL DEFAULT '1' COMMENT 'æ¶ˆæ¯ç±»å‹ï¼š1-æ–‡æœ¬ 2-å›¾ç‰‡ 3-æ–‡ä»¶ 4-è¯­éŸ³ 5-è§†é¢‘ 6-ä½ç½® 7-ç³»ç»Ÿé€šçŸ¥ 8-è‡ªå®šä¹‰',
  `sender_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'å‘é€è€…ç”¨æˆ· ID',
  `receiver_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'æ¥æ”¶è€… IDï¼ˆå•èŠä¸ºå¯¹æ–¹ userIdï¼Œç¾¤èŠä¸º groupIdï¼‰',
  `content` text COLLATE utf8mb4_unicode_ci COMMENT 'æ¶ˆæ¯å†…å®¹',
  `extra` text COLLATE utf8mb4_unicode_ci COMMENT 'æ‰©å±•å†…å®¹ï¼ˆJSON æ ¼å¼ï¼‰',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT 'æ¶ˆæ¯çŠ¶æ€ï¼š0-å‘é€ä¸­ 1-å·²å‘é€ 2-å·²é€è¾¾ 3-å·²è¯» 4-å¤±è´¥ 5-å·²æ’¤å› 6-å·²åˆ é™¤',
  `server_seq` bigint NOT NULL DEFAULT '0' COMMENT 'æœåŠ¡ç«¯æ¶ˆæ¯åºåˆ—å·',
  `reply_to` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'å¼•ç”¨æ¶ˆæ¯ ID',
  `send_time` datetime DEFAULT NULL COMMENT 'å‘é€æ—¶é—´',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'åˆ›å»ºæ—¶é—´',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'æ›´æ–°æ—¶é—´',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_message_id` (`message_id`),
  KEY `idx_conversation_seq` (`conversation_id`,`server_seq`),
  KEY `idx_sender_id` (`sender_id`),
  KEY `idx_send_time` (`send_time`)
) ENGINE=InnoDB AUTO_INCREMENT=43 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='IM æ¶ˆæ¯è¡¨';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `pcd_im_message`
--

LOCK TABLES `pcd_im_message` WRITE;
/*!40000 ALTER TABLE `pcd_im_message` DISABLE KEYS */;
INSERT INTO `pcd_im_message` VALUES (1,'335812749118541824','',0,0,'','','','',1,335812749118541825,'','2026-07-15 23:59:58','2026-07-15 23:59:58','2026-07-15 23:59:58'),(2,'335815067608158208','',0,0,'','','','',1,335815067608158209,'','2026-07-16 00:09:11','2026-07-16 00:09:11','2026-07-16 00:09:11'),(3,'335815073777979392','',0,0,'','','','',1,335815073777979393,'','2026-07-16 00:09:13','2026-07-16 00:09:13','2026-07-16 00:09:13'),(4,'335824389587210240','',0,0,'','','','',1,335824389587210241,'','2026-07-16 00:46:14','2026-07-16 00:46:14','2026-07-16 00:46:14'),(5,'335824625109962752','',0,0,'','','','',1,335824625114157056,'','2026-07-16 00:47:10','2026-07-16 00:47:10','2026-07-16 00:47:10'),(6,'335824835219427328','',0,0,'','','','',1,335824835219427329,'','2026-07-16 00:48:00','2026-07-16 00:48:00','2026-07-16 00:48:00'),(7,'335838964319653888','',0,0,'','','','',1,335838964319653889,'','2026-07-16 01:44:09','2026-07-16 01:44:09','2026-07-16 01:44:09'),(8,'335839216959361024','',0,0,'55555555-5555-5555-5555-555555555555','','è¯¥æ¶ˆæ¯å·²è¢«æ’¤å›','',5,335839216959361025,'','2026-07-16 01:45:09','2026-07-16 01:45:09','2026-07-16 01:45:57'),(9,'335839795207081984','',0,0,'55555555-5555-5555-5555-55555555555','','è¯¥æ¶ˆæ¯å·²è¢«æ’¤å›','',5,335839795207081985,'','2026-07-16 01:47:27','2026-07-16 01:47:27','2026-07-16 01:47:42'),(10,'335839926652375040','',0,0,'a','','è¯¥æ¶ˆæ¯å·²è¢«æ’¤å›','',5,335839926652375041,'','2026-07-16 01:47:58','2026-07-16 01:47:58','2026-07-16 01:48:11'),(11,'335842985881243648','88',0,0,'a','','','',1,335842985881243649,'','2026-07-16 02:00:07','2026-07-16 02:00:07','2026-07-16 02:00:07'),(12,'335865894670569472','55555555-5555-6666-5555-555555555555',1,1,'55555555-4444-5555-5555-555555555555','55555555-5555-5555-5555-555555555555','','',1,335865894670569473,'','2026-07-16 03:31:09','2026-07-16 03:31:09','2026-07-16 03:31:09'),(13,'335866066838360064','55555555-5555-6666-5555-555555555555',1,1,'55555555-4444-5555-5555-555555555555','55555555-5555-5555-5555-555555555555','','',1,335866066838360065,'','2026-07-16 03:31:50','2026-07-16 03:31:50','2026-07-16 03:31:50'),(14,'335866071926050816','55555555-5555-6666-5555-555555555555',1,1,'55555555-4444-5555-5555-555555555555','55555555-5555-5555-5555-555555555555','','',1,335866071926050817,'','2026-07-16 03:31:52','2026-07-16 03:31:52','2026-07-16 03:31:52'),(15,'335866075155664896','55555555-5555-6666-5555-555555555555',1,1,'55555555-4444-5555-5555-555555555555','55555555-5555-5555-5555-555555555555','','',1,335866075155664897,'','2026-07-16 03:31:52','2026-07-16 03:31:52','2026-07-16 03:31:52'),(16,'335866078200729600','55555555-5555-6666-5555-555555555555',1,1,'55555555-4444-5555-5555-555555555555','55555555-5555-5555-5555-555555555555','','',1,335866078200729601,'','2026-07-16 03:31:53','2026-07-16 03:31:53','2026-07-16 03:31:53'),(17,'335866080868306944','55555555-5555-6666-5555-555555555555',1,1,'55555555-4444-5555-5555-555555555555','55555555-5555-5555-5555-555555555555','','',1,335866080868306945,'','2026-07-16 03:31:54','2026-07-16 03:31:54','2026-07-16 03:31:54'),(18,'335866082709606400','55555555-5555-6666-5555-555555555555',1,1,'55555555-4444-5555-5555-555555555555','55555555-5555-5555-5555-555555555555','','',1,335866082709606401,'','2026-07-16 03:31:54','2026-07-16 03:31:54','2026-07-16 03:31:54'),(19,'335866083506524160','55555555-5555-6666-5555-555555555555',1,1,'55555555-4444-5555-5555-555555555555','55555555-5555-5555-5555-555555555555','','',1,335866083506524161,'','2026-07-16 03:31:54','2026-07-16 03:31:54','2026-07-16 03:31:54'),(20,'335866084261498880','55555555-5555-6666-5555-555555555555',1,1,'55555555-4444-5555-5555-555555555555','55555555-5555-5555-5555-555555555555','','',1,335866084261498881,'','2026-07-16 03:31:55','2026-07-16 03:31:55','2026-07-16 03:31:55'),(21,'335866085037445120','55555555-5555-6666-5555-555555555555',1,1,'55555555-4444-5555-5555-555555555555','55555555-5555-5555-5555-555555555555','','',1,335866085037445121,'','2026-07-16 03:31:55','2026-07-16 03:31:55','2026-07-16 03:31:55'),(22,'335866085595287552','55555555-5555-6666-5555-555555555555',1,1,'55555555-4444-5555-5555-555555555555','55555555-5555-5555-5555-555555555555','','',1,335866085595287553,'','2026-07-16 03:31:55','2026-07-16 03:31:55','2026-07-16 03:31:55'),(23,'335866086312513536','55555555-5555-6666-5555-555555555555',1,1,'55555555-4444-5555-5555-555555555555','55555555-5555-5555-5555-555555555555','','',1,335866086312513537,'','2026-07-16 03:31:55','2026-07-16 03:31:55','2026-07-16 03:31:55'),(24,'335866191279165440','55555555-5555-6666-5555-555555555555',1,1,'55555555-4444-5555-5555-555555555555','55555555-5555-5555-5555-555555555555','','',1,335866191279165441,'','2026-07-16 03:32:20','2026-07-16 03:32:20','2026-07-16 03:32:20'),(25,'335866194936598528','55555555-5555-6666-5555-555555555555',1,1,'55555555-4444-5555-5555-555555555555','55555555-5555-5555-5555-555555555555','','',1,335866194936598529,'','2026-07-16 03:32:21','2026-07-16 03:32:21','2026-07-16 03:32:21'),(26,'335866198929575936','55555555-5555-6666-5555-555555555555',1,1,'55555555-4444-5555-5555-555555555555','55555555-5555-5555-5555-555555555555','','',1,335866198929575937,'','2026-07-16 03:32:22','2026-07-16 03:32:22','2026-07-16 03:32:22'),(27,'335866201240637440','55555555-5555-6666-5555-555555555555',1,1,'55555555-4444-5555-5555-555555555555','55555555-5555-5555-5555-555555555555','','',1,335866201240637441,'','2026-07-16 03:32:22','2026-07-16 03:32:22','2026-07-16 03:32:22'),(28,'335866203757219840','55555555-5555-6666-5555-555555555555',1,1,'55555555-4444-5555-5555-555555555555','55555555-5555-5555-5555-555555555555','','',1,335866203757219841,'','2026-07-16 03:32:23','2026-07-16 03:32:23','2026-07-16 03:32:23'),(29,'335866248023904256','55555555-5555-6666-5555-555555555555',1,1,'55555555-4444-5555-5555-555555555555','55555555-5555-5555-5555-555555555556','','',1,335866248023904257,'','2026-07-16 03:32:34','2026-07-16 03:32:34','2026-07-16 03:32:34'),(30,'335866522604015616','55555555-5555-6666-5555-555555555555',1,1,'55555555-4444-5555-5555-555555555555','55555555-5555-5555-5555-555555555556','','',1,335866522604015617,'','2026-07-16 03:33:39','2026-07-16 03:33:39','2026-07-16 03:33:39'),(31,'335866540991844352','55555555-5555-6666-5555-555555555555',1,1,'55555555-4444-5555-5555-555555555555','55555555-5555-5555-5555-555555555555','','',1,335866540991844353,'','2026-07-16 03:33:43','2026-07-16 03:33:43','2026-07-16 03:33:43'),(32,'335866561149669376','55555555-5555-6666-5555-555555555555',1,1,'55555555-4444-5555-5555-555555555555','55555555-5555-5555-5555-555555555555','','',1,335866561149669377,'','2026-07-16 03:33:48','2026-07-16 03:33:48','2026-07-16 03:33:48'),(33,'335866563624308736','55555555-5555-6666-5555-555555555555',1,1,'55555555-4444-5555-5555-555555555555','55555555-5555-5555-5555-555555555555','','',1,335866563624308737,'','2026-07-16 03:33:49','2026-07-16 03:33:49','2026-07-16 03:33:49'),(34,'335866618368364544','55555555-5555-6666-5555-555555555555',1,1,'55555555-4444-5555-5555-555555555555','55555555-5555-5555-5555-555555555555','','',1,335866618368364545,'','2026-07-16 03:34:02','2026-07-16 03:34:02','2026-07-16 03:34:02'),(35,'335866737633398784','55555555-5555-6666-5555-555555555555',1,1,'55555555-4444-5555-5555-555555555555','55555555-5555-5555-5555-555555555555','','',1,335866737633398785,'','2026-07-16 03:34:30','2026-07-16 03:34:30','2026-07-16 03:34:30'),(36,'335867699064344576','55555555-5555-6666-5555-555555555555',1,1,'55555555-4444-5555-5555-555555555555','55555555-5555-5555-5555-555555555555','','',1,335867699064344577,'','2026-07-16 03:38:20','2026-07-16 03:38:20','2026-07-16 03:38:20'),(37,'335867801992564736','55555555-5555-6666-5555-555555555555',1,1,'55555555-4444-5555-5555-555555555555','55555555-5555-5555-5555-555555555555','23424424242424','',1,335867801992564737,'','2026-07-16 03:38:44','2026-07-16 03:38:44','2026-07-16 03:38:44'),(38,'336149695976050688','55555555-5555-6666-5555-555555555555',1,1,'55555555-4444-5555-5555-555555555555','55555555-5555-5555-5555-555555555555','23424424242424','',1,336149695976050689,'','2026-07-16 22:18:53','2026-07-16 22:18:53','2026-07-16 22:18:53'),(39,'336149728565792768','55555555-5555-6666-5555-555555555555',1,1,'55555555-4444-5555-5555-555555555555','55555555-5555-5555-5555-555555555555','23424424242424','',1,336149728569987072,'','2026-07-16 22:19:01','2026-07-16 22:19:01','2026-07-16 22:19:01'),(40,'336152919927820288','55555555-5555-6666-5555-555555555555',1,1,'55555555-4444-5555-5555-555555555555','55555555-5555-5555-5555-555555555555','23424424242424','',1,336152919927820289,'','2026-07-16 22:31:42','2026-07-16 22:31:42','2026-07-16 22:31:42'),(41,'336156097691914240','55555555-5555-6666-5555-555555555555',1,1,'55555555-4444-5555-5555-555555555555','55555555-5555-5555-5555-555555555555','23424424242424','',1,336156097691914241,'','2026-07-16 22:44:19','2026-07-16 22:44:19','2026-07-16 22:44:19'),(42,'336157184578686976','55555555-5555-6666-5555-555555555555',1,1,'55555555-4444-5555-5555-555555555555','55555555-5555-5555-5555-555555555555','23424424242424','',1,336157184578686977,'','2026-07-16 22:48:38','2026-07-16 22:48:38','2026-07-16 22:48:38');
/*!40000 ALTER TABLE `pcd_im_message` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `pcd_login_audit_table`
--

DROP TABLE IF EXISTS `pcd_login_audit_table`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `pcd_login_audit_table` (
  `audit_id` bigint NOT NULL AUTO_INCREMENT,
  `audit_user_id` binary(16) DEFAULT NULL COMMENT 'åŒ¹é…åˆ°çš„ç”¨æˆ·ID',
  `audit_account` varchar(100) DEFAULT NULL COMMENT 'ç™»å½•è´¦å·',
  `audit_phone_number` varchar(50) DEFAULT NULL COMMENT 'ç™»å½•æ‰‹æœºå·',
  `audit_success` tinyint(1) NOT NULL COMMENT 'æ˜¯å¦ç™»å½•æˆåŠŸ',
  `audit_failure_reason` varchar(120) DEFAULT NULL COMMENT 'å¤±è´¥åŸå› ',
  `audit_client_ip` varchar(64) DEFAULT NULL COMMENT 'å®¢æˆ·ç«¯IP',
  `audit_user_agent` varchar(512) DEFAULT NULL COMMENT 'User-Agent',
  `audit_created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`audit_id`),
  KEY `idx_login_audit_user_time` (`audit_user_id`,`audit_created_at`),
  KEY `idx_login_audit_account_time` (`audit_account`,`audit_created_at`),
  KEY `idx_login_audit_ip_time` (`audit_client_ip`,`audit_created_at`),
  CONSTRAINT `pcd_login_audit_table_ibfk_1` FOREIGN KEY (`audit_user_id`) REFERENCES `pcd_user_info_table` (`user_id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='ç™»å½•å®¡è®¡è¡¨';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `pcd_login_audit_table`
--

LOCK TABLES `pcd_login_audit_table` WRITE;
/*!40000 ALTER TABLE `pcd_login_audit_table` DISABLE KEYS */;
/*!40000 ALTER TABLE `pcd_login_audit_table` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `pcd_login_session_table`
--

DROP TABLE IF EXISTS `pcd_login_session_table`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `pcd_login_session_table` (
  `login_session_id` binary(16) NOT NULL COMMENT 'ä¼šè¯ID',
  `login_session_user_id` binary(16) NOT NULL COMMENT 'ç™»å½•ç”¨æˆ·ID',
  `login_session_device_id` binary(16) DEFAULT NULL COMMENT 'å…³è”è®¾å¤‡ID',
  `login_session_token_jti` binary(16) DEFAULT NULL COMMENT 'JWT jti',
  `login_session_client_ip` varchar(64) DEFAULT NULL COMMENT 'ç™»å½•IP',
  `login_session_user_agent` varchar(512) DEFAULT NULL COMMENT 'User-Agent',
  `login_session_started_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `login_session_expires_at` datetime NOT NULL COMMENT 'ä¼šè¯è¿‡æœŸæ—¶é—´',
  `login_session_revoked_at` datetime DEFAULT NULL COMMENT 'ä¼šè¯æ’¤é”€æ—¶é—´',
  `login_session_status` enum('active','expired','revoked') NOT NULL DEFAULT 'active',
  PRIMARY KEY (`login_session_id`),
  KEY `idx_login_session_user_status` (`login_session_user_id`,`login_session_status`),
  KEY `idx_login_session_device_status` (`login_session_device_id`,`login_session_status`),
  KEY `idx_login_session_jti` (`login_session_token_jti`),
  CONSTRAINT `pcd_login_session_table_ibfk_1` FOREIGN KEY (`login_session_user_id`) REFERENCES `pcd_user_info_table` (`user_id`) ON DELETE CASCADE,
  CONSTRAINT `pcd_login_session_table_ibfk_2` FOREIGN KEY (`login_session_device_id`) REFERENCES `pcd_user_device_table` (`device_id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='ç”¨æˆ·ç™»å½•ä¼šè¯è¡¨';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `pcd_login_session_table`
--

LOCK TABLES `pcd_login_session_table` WRITE;
/*!40000 ALTER TABLE `pcd_login_session_table` DISABLE KEYS */;
/*!40000 ALTER TABLE `pcd_login_session_table` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `pcd_notification_aggregation_windows`
--

DROP TABLE IF EXISTS `pcd_notification_aggregation_windows`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `pcd_notification_aggregation_windows` (
  `id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'èšåˆçª—å£ID (UUID)',
  `user_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'ç”¨æˆ·ID',
  `channel` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'æ¸ é“',
  `type` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'é€šçŸ¥ç±»å‹',
  `record_ids` json DEFAULT NULL COMMENT 'èšåˆçš„é€šçŸ¥è®°å½•IDåˆ—è¡¨',
  `count` int NOT NULL DEFAULT '0' COMMENT 'èšåˆæ•°é‡',
  `status` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'open' COMMENT 'çŠ¶æ€: open, closed, sent',
  `window_start` datetime NOT NULL COMMENT 'çª—å£å¼€å§‹æ—¶é—´',
  `window_end` datetime NOT NULL COMMENT 'çª—å£ç»“æŸæ—¶é—´',
  `sent_at` datetime DEFAULT NULL COMMENT 'å®é™…å‘é€æ—¶é—´',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_status` (`status`),
  KEY `idx_window_end` (`window_end`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='æ¶ˆæ¯èšåˆçª—å£è¡¨';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `pcd_notification_aggregation_windows`
--

LOCK TABLES `pcd_notification_aggregation_windows` WRITE;
/*!40000 ALTER TABLE `pcd_notification_aggregation_windows` DISABLE KEYS */;
/*!40000 ALTER TABLE `pcd_notification_aggregation_windows` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `pcd_notification_delivery_logs`
--

DROP TABLE IF EXISTS `pcd_notification_delivery_logs`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `pcd_notification_delivery_logs` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `notification_id` bigint NOT NULL COMMENT 'å…³è”é€šçŸ¥è®°å½•ID',
  `event_id` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'äº‹ä»¶ID',
  `channel` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'æ¸ é“',
  `status` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'çŠ¶æ€: sent, delivered, failed',
  `provider_response` text COLLATE utf8mb4_unicode_ci COMMENT 'ç¬¬ä¸‰æ–¹æœåŠ¡å•†åŸå§‹å“åº”',
  `error_msg` varchar(1000) COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT 'é”™è¯¯ä¿¡æ¯',
  `duration_ms` bigint NOT NULL DEFAULT '0' COMMENT 'å‘é€è€—æ—¶ï¼ˆæ¯«ç§’ï¼‰',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_notification_id` (`notification_id`),
  KEY `idx_event_id` (`event_id`),
  KEY `idx_channel` (`channel`),
  KEY `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='é€è¾¾æ—¥å¿—è¡¨';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `pcd_notification_delivery_logs`
--

LOCK TABLES `pcd_notification_delivery_logs` WRITE;
/*!40000 ALTER TABLE `pcd_notification_delivery_logs` DISABLE KEYS */;
/*!40000 ALTER TABLE `pcd_notification_delivery_logs` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `pcd_notification_device_subscriptions`
--

DROP TABLE IF EXISTS `pcd_notification_device_subscriptions`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `pcd_notification_device_subscriptions` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'ç”¨æˆ·ID',
  `device_token` varchar(512) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'è®¾å¤‡ Token (APNs/FCM/WebPush)',
  `platform` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'å¹³å°: ios, android, web',
  `app_version` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT 'App ç‰ˆæœ¬',
  `is_active` tinyint(1) NOT NULL DEFAULT '1' COMMENT 'æ˜¯å¦æ´»è·ƒ',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_device` (`user_id`,`device_token`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_platform` (`platform`),
  KEY `idx_is_active` (`is_active`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='è®¾å¤‡è®¢é˜…è¡¨';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `pcd_notification_device_subscriptions`
--

LOCK TABLES `pcd_notification_device_subscriptions` WRITE;
/*!40000 ALTER TABLE `pcd_notification_device_subscriptions` DISABLE KEYS */;
/*!40000 ALTER TABLE `pcd_notification_device_subscriptions` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `pcd_notification_preferences`
--

DROP TABLE IF EXISTS `pcd_notification_preferences`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `pcd_notification_preferences` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'ç”¨æˆ·ID',
  `channel` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'æ¸ é“',
  `enabled` tinyint(1) NOT NULL DEFAULT '1' COMMENT 'æ˜¯å¦å¯ç”¨è¯¥æ¸ é“',
  `dnd_start` varchar(5) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '22:00' COMMENT 'å…æ‰“æ‰°å¼€å§‹ HH:MM',
  `dnd_end` varchar(5) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '07:00' COMMENT 'å…æ‰“æ‰°ç»“æŸ HH:MM',
  `dnd_enabled` tinyint(1) NOT NULL DEFAULT '0' COMMENT 'æ˜¯å¦å¼€å¯å…æ‰“æ‰°',
  `max_per_day` int NOT NULL DEFAULT '50' COMMENT 'æ¯æ—¥æœ€å¤§æ¨é€æ•°',
  `quiet_hours_json` json DEFAULT NULL COMMENT 'é™éŸ³æ—¶æ®µ: ["22:00-07:00"]',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_channel` (`user_id`,`channel`),
  KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='ç”¨æˆ·é€šçŸ¥åå¥½è¡¨';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `pcd_notification_preferences`
--

LOCK TABLES `pcd_notification_preferences` WRITE;
/*!40000 ALTER TABLE `pcd_notification_preferences` DISABLE KEYS */;
/*!40000 ALTER TABLE `pcd_notification_preferences` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `pcd_notification_records`
--

DROP TABLE IF EXISTS `pcd_notification_records`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `pcd_notification_records` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `event_id` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'äº‹ä»¶å”¯ä¸€IDï¼Œç”¨äºå¹‚ç­‰',
  `user_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'ç”¨æˆ·ID',
  `channel` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'æ¸ é“',
  `type` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'é€šçŸ¥ç±»å‹: verification, welcome, share, system, etc.',
  `title` varchar(500) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT 'é€šçŸ¥æ ‡é¢˜',
  `body` text COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'é€šçŸ¥æ­£æ–‡',
  `recipient` varchar(500) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'æ¥æ”¶è€…æ ‡è¯†ï¼ˆé‚®ç®±/æ‰‹æœºå·/deviceTokenï¼‰',
  `template_code` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT 'ä½¿ç”¨çš„æ¨¡æ¿ CODE',
  `status` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'pending' COMMENT 'çŠ¶æ€: pending, processing, sent, delivered, failed, cancelled, aggregated',
  `priority` int NOT NULL DEFAULT '5' COMMENT 'ä¼˜å…ˆçº§: 0=ä½, 5=æ­£å¸¸, 10=é«˜',
  `retry_count` int NOT NULL DEFAULT '0' COMMENT 'å·²é‡è¯•æ¬¡æ•°',
  `max_retries` int NOT NULL DEFAULT '3' COMMENT 'æœ€å¤§é‡è¯•æ¬¡æ•°',
  `error_msg` varchar(1000) COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT 'é”™è¯¯ä¿¡æ¯',
  `aggregation_id` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT 'èšåˆæ‰¹æ¬¡ID',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_event_id_channel` (`event_id`,`channel`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_status` (`status`),
  KEY `idx_channel_status` (`channel`,`status`),
  KEY `idx_created_at` (`created_at`),
  KEY `idx_aggregation_id` (`aggregation_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='é€šçŸ¥è®°å½•è¡¨';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `pcd_notification_records`
--

LOCK TABLES `pcd_notification_records` WRITE;
/*!40000 ALTER TABLE `pcd_notification_records` DISABLE KEYS */;
/*!40000 ALTER TABLE `pcd_notification_records` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `pcd_notification_send_log_table`
--

DROP TABLE IF EXISTS `pcd_notification_send_log_table`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `pcd_notification_send_log_table` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'ä¸»é”®è‡ªå¢ID',
  `event_id` varchar(255) NOT NULL COMMENT 'äº‹ä»¶å”¯ä¸€ID',
  `channel` varchar(20) NOT NULL COMMENT 'é€šé“',
  `receiver` varchar(255) NOT NULL COMMENT 'æ¥æ”¶è€…',
  `user_id` binary(16) DEFAULT NULL COMMENT 'å…³è”ç”¨æˆ·ID',
  `status` varchar(20) NOT NULL COMMENT 'çŠ¶æ€',
  `retry_count` int NOT NULL DEFAULT '0' COMMENT 'é‡è¯•æ¬¡æ•°',
  `error_message` varchar(1000) DEFAULT NULL COMMENT 'é”™è¯¯ä¿¡æ¯',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'åˆ›å»ºæ—¶é—´',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'æ›´æ–°æ—¶é—´',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_event_channel_receiver` (`event_id`,`channel`,`receiver`),
  KEY `idx_status` (`status`),
  KEY `idx_created_at` (`created_at`)
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='é€šçŸ¥å‘é€æ—¥å¿—è¡¨';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `pcd_notification_send_log_table`
--

LOCK TABLES `pcd_notification_send_log_table` WRITE;
/*!40000 ALTER TABLE `pcd_notification_send_log_table` DISABLE KEYS */;
INSERT INTO `pcd_notification_send_log_table` VALUES (1,'email-verify:hellomwz@outlook.com:1781265551034','EMAIL','hellomwz@outlook.com',NULL,'SUCCESS',0,NULL,'2026-06-12 19:59:14','2026-06-12 19:59:20'),(2,'email-verify:hellomwz@outlook.com:1781265722479','EMAIL','hellomwz@outlook.com',NULL,'SUCCESS',0,NULL,'2026-06-12 20:02:03','2026-06-12 20:02:06'),(3,'user-registered:37e06d5a-689f-4395-bb11-4010e1016cd6:1782055830496','EMAIL','hellomwz@outlook.com',_binary '7\àmZhŸC•»@\ál\Ö','SUCCESS',0,NULL,'2026-06-21 23:30:31','2026-06-21 23:30:31');
/*!40000 ALTER TABLE `pcd_notification_send_log_table` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `pcd_notification_templates`
--

DROP TABLE IF EXISTS `pcd_notification_templates`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `pcd_notification_templates` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `code` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'æ¨¡æ¿å”¯ä¸€æ ‡è¯†ï¼Œå¦‚ welcome_email, verification_sms',
  `name` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'æ¨¡æ¿åç§°',
  `channel` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'æ¸ é“: email, sms, push, wechat_mp, alipay_mp, webpush',
  `lang` varchar(10) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'zh-CN' COMMENT 'è¯­è¨€: zh-CN, en-US, ja-JP',
  `title` varchar(500) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT 'æ ‡é¢˜æ¨¡æ¿ï¼Œæ”¯æŒ {{.var}} å˜é‡',
  `body` text COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'æ­£æ–‡æ¨¡æ¿ï¼Œæ”¯æŒ {{.var}} å˜é‡',
  `html_body` text COLLATE utf8mb4_unicode_ci COMMENT 'HTML æ¨¡æ¿ï¼ˆé‚®ä»¶æ¸ é“ä¸“ç”¨ï¼‰',
  `variables_json` json DEFAULT NULL COMMENT 'æ¨¡æ¿å˜é‡å®šä¹‰: [{"name":"code","type":"string","required":true}]',
  `is_active` tinyint(1) NOT NULL DEFAULT '1' COMMENT 'æ˜¯å¦å¯ç”¨',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_code_channel_lang` (`code`,`channel`,`lang`),
  KEY `idx_channel` (`channel`),
  KEY `idx_is_active` (`is_active`)
) ENGINE=InnoDB AUTO_INCREMENT=10 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='æ¶ˆæ¯æ¨¡æ¿è¡¨';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `pcd_notification_templates`
--

LOCK TABLES `pcd_notification_templates` WRITE;
/*!40000 ALTER TABLE `pcd_notification_templates` DISABLE KEYS */;
INSERT INTO `pcd_notification_templates` VALUES (1,'welcome_email','æ³¨å†Œæ¬¢è¿é‚®ä»¶','email','zh-CN','æ¬¢è¿ä½¿ç”¨ç§æœ‰äº‘ç½‘ç›˜','æ¬¢è¿åŠ å…¥ç§æœ‰äº‘ç½‘ç›˜ï¼Œæ‚¨çš„è´¦å·å·²æˆåŠŸåˆ›å»ºã€‚','<!DOCTYPE html>\n<html lang=\"zh-CN\">\n<head>\n    <meta charset=\"UTF-8\">\n    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n    <title>æ¬¢è¿ä½¿ç”¨ç§æœ‰äº‘ç½‘ç›˜</title>\n    <style>\n        body, table, td, a {\n            -webkit-text-size-adjust: 100%;\n            -ms-text-size-adjust: 100%;\n        }\n        table, td {\n            mso-table-lspace: 0pt;\n            mso-table-rspace: 0pt;\n        }\n        img {\n            -ms-interpolation-mode: bicubic;\n            border: 0;\n            outline: none;\n            text-decoration: none;\n        }\n        body {\n            margin: 0 !important;\n            padding: 0 !important;\n            width: 100% !important;\n            background-color: #f3f5f8;\n            font-family: -apple-system, BlinkMacSystemFont, \"Segoe UI\", \"PingFang SC\",\n            \"Hiragino Sans GB\", \"Microsoft YaHei\", Arial, sans-serif;\n        }\n        a {\n            color: #2563eb;\n            text-decoration: none;\n        }\n        @media screen and (max-width: 600px) {\n            .container {\n                width: 100% !important;\n            }\n            .outer-padding {\n                padding: 16px !important;\n            }\n            .mobile-padding {\n                padding-left: 22px !important;\n                padding-right: 22px !important;\n            }\n            .title {\n                font-size: 22px !important;\n            }\n            .button {\n                display: block !important;\n                width: 100% !important;\n                box-sizing: border-box !important;\n            }\n            .feature-column {\n                display: block !important;\n                width: 100% !important;\n                padding-right: 0 !important;\n                padding-left: 0 !important;\n                padding-bottom: 12px !important;\n            }\n        }\n    </style>\n</head>\n<body style=\"margin:0; padding:0; background-color:#f3f5f8;\">\n<div style=\"display:none; max-height:0; overflow:hidden; opacity:0; color:transparent; line-height:1px; font-size:1px;\">\n    æ¬¢è¿åŠ å…¥ç§æœ‰äº‘ç½‘ç›˜ï¼Œæ‚¨çš„è´¦å·å·²æˆåŠŸåˆ›å»ºã€‚\n</div>\n<table role=\"presentation\" width=\"100%\" cellspacing=\"0\" cellpadding=\"0\" border=\"0\" style=\"background-color:#f3f5f8;\">\n    <tr>\n        <td align=\"center\" class=\"outer-padding\" style=\"padding:32px 12px;\">\n            <table role=\"presentation\" class=\"container\" width=\"600\" cellspacing=\"0\" cellpadding=\"0\" border=\"0\"\n                   style=\"width:600px; max-width:600px; background-color:#ffffff; border-radius:18px; overflow:hidden; border:1px solid #e5e7eb;\">\n                <tr>\n                    <td class=\"mobile-padding\" style=\"padding:30px 36px 22px 36px; background-color:#ffffff; border-bottom:1px solid #eef0f4;\">\n                        <table role=\"presentation\" width=\"100%\" cellspacing=\"0\" cellpadding=\"0\" border=\"0\">\n                            <tr>\n                                <td valign=\"middle\" width=\"48\">\n                                    <table role=\"presentation\" cellspacing=\"0\" cellpadding=\"0\" border=\"0\">\n                                        <tr>\n                                            <td align=\"center\" valign=\"middle\"\n                                                style=\"width:44px; height:44px; background-color:#111827; border-radius:12px; color:#ffffff; font-size:15px; font-weight:700; letter-spacing:0.5px;\">\n                                                PCD\n                                            </td>\n                                        </tr>\n                                    </table>\n                                </td>\n                                <td valign=\"middle\" style=\"padding-left:12px;\">\n                                    <div style=\"font-size:16px; font-weight:700; color:#111827; line-height:1.4;\">\n                                        ç§æœ‰äº‘ç½‘ç›˜\n                                    </div>\n                                    <div style=\"font-size:12px; color:#6b7280; line-height:1.5;\">\n                                        Private Cloud Disk &middot; å®‰å…¨å¯é çš„æ–‡ä»¶å­˜å‚¨æœåŠ¡\n                                    </div>\n                                </td>\n                            </tr>\n                        </table>\n                    </td>\n                </tr>\n                <tr>\n                    <td class=\"mobile-padding\" style=\"padding:38px 36px 18px 36px;\">\n                        <div style=\"display:inline-block; padding:6px 12px; background-color:#ecfdf5; border:1px solid #bbf7d0; border-radius:999px; color:#047857; font-size:13px; font-weight:600; line-height:1.2;\">\n                            æ³¨å†ŒæˆåŠŸ\n                        </div>\n                        <h1 class=\"title\" style=\"margin:18px 0 12px 0; font-size:26px; line-height:1.35; color:#111827; font-weight:700;\">\n                            æ¬¢è¿ä½¿ç”¨ç§æœ‰äº‘ç½‘ç›˜\n                        </h1>\n                        <p style=\"margin:0; font-size:14px; line-height:1.85; color:#4b5563;\">\n                            æ‚¨å¥½\n                            {{.Username}}ï¼Œ\n                            æ‚¨çš„è´¦å·å·²æˆåŠŸåˆ›å»ºã€‚ç°åœ¨å¯ä»¥å¼€å§‹ä¸Šä¼ ã€ç®¡ç†å’Œè®¿é—®æ‚¨çš„æ–‡ä»¶èµ„æºã€‚\n                        </p>\n                    </td>\n                </tr>\n                <tr>\n                    <td class=\"mobile-padding\" style=\"padding:12px 36px 10px 36px;\">\n                        <table role=\"presentation\" width=\"100%\" cellspacing=\"0\" cellpadding=\"0\" border=\"0\"\n                               style=\"background-color:#f9fafb; border:1px solid #e5e7eb; border-radius:16px;\">\n                            <tr>\n                                <td style=\"padding:20px 20px 18px 20px;\">\n                                    <div style=\"font-size:14px; font-weight:700; color:#111827; margin-bottom:12px;\">\n                                        è´¦å·ä¿¡æ¯\n                                    </div>\n                                    <table role=\"presentation\" width=\"100%\" cellspacing=\"0\" cellpadding=\"0\" border=\"0\">\n                                        <tr>\n                                            <td style=\"padding:8px 0; font-size:13px; color:#6b7280; width:90px;\">\n                                                è´¦å·é‚®ç®±\n                                            </td>\n                                            <td style=\"padding:8px 0; font-size:13px; color:#111827; font-weight:600; word-break:break-all;\">\n                                                {{.Email}}\n                                            </td>\n                                        </tr>\n                                        <tr>\n                                            <td style=\"padding:8px 0; font-size:13px; color:#6b7280; width:90px;\">\n                                                æ³¨å†Œæ—¶é—´\n                                            </td>\n                                            <td style=\"padding:8px 0; font-size:13px; color:#111827; font-weight:600;\">\n                                                {{.RegisterTime}}\n                                            </td>\n                                        </tr>\n                                        <tr>\n                                            <td style=\"padding:8px 0; font-size:13px; color:#6b7280; width:90px;\">\n                                                è´¦å·çŠ¶æ€\n                                            </td>\n                                            <td style=\"padding:8px 0; font-size:13px; color:#047857; font-weight:700;\">\n                                                å·²å¯ç”¨\n                                            </td>\n                                        </tr>\n                                    </table>\n                                </td>\n                            </tr>\n                        </table>\n                    </td>\n                </tr>\n                <tr>\n                    <td class=\"mobile-padding\" align=\"center\" style=\"padding:22px 36px 12px 36px;\">\n                        <a class=\"button\"\n                           href=\"{{.LoginUrl}}\"\n                           target=\"_blank\"\n                           style=\"display:inline-block; background-color:#111827; color:#ffffff; font-size:14px; font-weight:700; line-height:1.2; padding:15px 28px; border-radius:12px; text-align:center; text-decoration:none;\">\n                            è¿›å…¥ç§æœ‰äº‘ç½‘ç›˜\n                        </a>\n                        <div style=\"font-size:12px; color:#9ca3af; line-height:1.7; margin-top:12px;\">\n                            å¦‚æœæŒ‰é’®æ— æ³•æ‰“å¼€ï¼Œè¯·å¤åˆ¶ç™»å½•åœ°å€åˆ°æµè§ˆå™¨è®¿é—®ã€‚\n                        </div>\n                    </td>\n                </tr>\n                <tr>\n                    <td class=\"mobile-padding\" style=\"padding:18px 36px 8px 36px;\">\n                        <div style=\"font-size:14px; font-weight:700; color:#111827; margin-bottom:12px;\">\n                            æ‚¨å¯ä»¥å¼€å§‹ä½¿ç”¨\n                        </div>\n                        <table role=\"presentation\" width=\"100%\" cellspacing=\"0\" cellpadding=\"0\" border=\"0\">\n                            <tr>\n                                <td class=\"feature-column\" width=\"33.33%\" valign=\"top\" style=\"padding-right:8px;\">\n                                    <table role=\"presentation\" width=\"100%\" cellspacing=\"0\" cellpadding=\"0\" border=\"0\"\n                                           style=\"background-color:#ffffff; border:1px solid #e5e7eb; border-radius:14px;\">\n                                        <tr>\n                                            <td style=\"padding:16px 14px;\">\n                                                <div style=\"font-size:13px; font-weight:700; color:#111827; line-height:1.5;\">\n                                                    æ–‡ä»¶ä¸Šä¼ \n                                                </div>\n                                                <div style=\"font-size:12px; color:#6b7280; line-height:1.7; margin-top:5px;\">\n                                                    æ”¯æŒæ–‡ä»¶å®‰å…¨å­˜å‚¨ä¸ç»Ÿä¸€ç®¡ç†\n                                                </div>\n                                            </td>\n                                        </tr>\n                                    </table>\n                                </td>\n                                <td class=\"feature-column\" width=\"33.33%\" valign=\"top\" style=\"padding-left:4px; padding-right:4px;\">\n                                    <table role=\"presentation\" width=\"100%\" cellspacing=\"0\" cellpadding=\"0\" border=\"0\"\n                                           style=\"background-color:#ffffff; border:1px solid #e5e7eb; border-radius:14px;\">\n                                        <tr>\n                                            <td style=\"padding:16px 14px;\">\n                                                <div style=\"font-size:13px; font-weight:700; color:#111827; line-height:1.5;\">\n                                                    å¿«é€Ÿè®¿é—®\n                                                </div>\n                                                <div style=\"font-size:12px; color:#6b7280; line-height:1.7; margin-top:5px;\">\n                                                    åœ¨ä¸åŒè®¾å¤‡ä¸Šè®¿é—®æ‚¨çš„èµ„æº\n                                                </div>\n                                            </td>\n                                        </tr>\n                                    </table>\n                                </td>\n                                <td class=\"feature-column\" width=\"33.33%\" valign=\"top\" style=\"padding-left:8px;\">\n                                    <table role=\"presentation\" width=\"100%\" cellspacing=\"0\" cellpadding=\"0\" border=\"0\"\n                                           style=\"background-color:#ffffff; border:1px solid #e5e7eb; border-radius:14px;\">\n                                        <tr>\n                                            <td style=\"padding:16px 14px;\">\n                                                <div style=\"font-size:13px; font-weight:700; color:#111827; line-height:1.5;\">\n                                                    å®‰å…¨ä¿æŠ¤\n                                                </div>\n                                                <div style=\"font-size:12px; color:#6b7280; line-height:1.7; margin-top:5px;\">\n                                                    ä¿éšœè´¦å·ä¸æ–‡ä»¶è®¿é—®å®‰å…¨\n                                                </div>\n                                            </td>\n                                        </tr>\n                                    </table>\n                                </td>\n                            </tr>\n                        </table>\n                    </td>\n                </tr>\n                <tr>\n                    <td class=\"mobile-padding\" style=\"padding:18px 36px 30px 36px;\">\n                        <table role=\"presentation\" width=\"100%\" cellspacing=\"0\" cellpadding=\"0\" border=\"0\"\n                               style=\"background-color:#f8fafc; border:1px solid #e5e7eb; border-radius:14px;\">\n                            <tr>\n                                <td style=\"padding:16px 16px 14px 16px;\">\n                                    <div style=\"font-size:14px; font-weight:700; color:#111827; margin-bottom:8px;\">\n                                        å®‰å…¨å»ºè®®\n                                    </div>\n                                    <div style=\"font-size:13px; line-height:1.8; color:#4b5563;\">\n                                        ä¸ºäº†ä¿æŠ¤æ‚¨çš„è´¦å·å’Œæ–‡ä»¶å®‰å…¨ï¼Œå»ºè®®æ‚¨å¦¥å–„ä¿ç®¡ç™»å½•å¯†ç ï¼Œä¸è¦ä¸ä»–äººå…±äº«è´¦å·ã€‚\n                                        å¦‚æœå‘ç°å¼‚å¸¸ç™»å½•æˆ–æ–‡ä»¶è®¿é—®è¡Œä¸ºï¼Œè¯·åŠæ—¶è”ç³»ç®¡ç†å‘˜å¤„ç†ã€‚\n                                    </div>\n                                </td>\n                            </tr>\n                        </table>\n                        <div style=\"height:1px; background-color:#eef0f4; margin:26px 0 18px 0;\"></div>\n                        <p style=\"margin:0; font-size:12px; line-height:1.8; color:#9ca3af; text-align:center;\">\n                            æ­¤é‚®ä»¶ç”±ç³»ç»Ÿè‡ªåŠ¨å‘é€ï¼Œè¯·å‹¿ç›´æ¥å›å¤ã€‚<br>\n                            å¦‚æœæ‚¨å¹¶æœªæ³¨å†Œç§æœ‰äº‘ç½‘ç›˜è´¦å·ï¼Œè¯·å¿½ç•¥æ­¤é‚®ä»¶æˆ–è”ç³»ç®¡ç†å‘˜ã€‚\n                        </p>\n                    </td>\n                </tr>\n                <tr>\n                    <td style=\"padding:22px 36px; background-color:#f9fafb; border-top:1px solid #eef0f4; text-align:center;\">\n                        <div style=\"font-size:13px; color:#111827; font-weight:700; line-height:1.6;\">\n                            ç§æœ‰äº‘ç½‘ç›˜ Private Cloud Disk\n                        </div>\n                        <div style=\"font-size:11px; color:#9ca3af; line-height:1.8; margin-top:4px;\">\n                            Account Service Center\n                        </div>\n                        <div style=\"font-size:11px; color:#9ca3af; line-height:1.8; margin-top:8px;\">\n                            &copy; {{.CurrentYear}} Private Cloud Disk. All rights reserved.\n                        </div>\n                    </td>\n                </tr>\n            </table>\n        </td>\n    </tr>\n</table>\n</body>\n</html>','[{\"desc\": \"ç”¨æˆ·å\", \"name\": \"Username\", \"type\": \"string\", \"required\": true}, {\"desc\": \"ç”¨æˆ·é‚®ç®±\", \"name\": \"Email\", \"type\": \"string\", \"required\": true}, {\"desc\": \"æ³¨å†Œæ—¶é—´\", \"name\": \"RegisterTime\", \"type\": \"string\", \"required\": true}, {\"desc\": \"ç™»å½•é¡µé¢URL\", \"name\": \"LoginUrl\", \"type\": \"string\", \"required\": true}, {\"desc\": \"å½“å‰å¹´ä»½\", \"name\": \"CurrentYear\", \"type\": \"string\", \"required\": true}]',1,'2026-06-22 17:51:01','2026-06-22 17:51:46'),(2,'verification_email','éªŒè¯ç é‚®ä»¶ï¼ˆäº®è‰²ç‰ˆï¼‰','email','zh-CN','éªŒè¯ç  - ç§æœ‰äº‘ç½‘ç›˜','æ‚¨æ­£åœ¨è¿›è¡Œå®‰å…¨éªŒè¯æ“ä½œï¼ŒéªŒè¯ç ï¼š{{.VerificationCode}}ï¼Œæœ‰æ•ˆæœŸ {{.ExpireMinutes}} åˆ†é’Ÿã€‚','<!DOCTYPE html>\n<html lang=\"zh-CN\">\n<head>\n    <meta charset=\"UTF-8\">\n    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n    <title>éªŒè¯ç  - ç§æœ‰äº‘ç½‘ç›˜</title>\n    <style>\n        body, table, td, a {\n            -webkit-text-size-adjust: 100%;\n            -ms-text-size-adjust: 100%;\n        }\n        table, td {\n            mso-table-lspace: 0pt;\n            mso-table-rspace: 0pt;\n        }\n        img {\n            -ms-interpolation-mode: bicubic;\n            border: 0;\n            outline: none;\n            text-decoration: none;\n        }\n        body {\n            margin: 0 !important;\n            padding: 0 !important;\n            width: 100% !important;\n            background-color: #ffffff;\n            font-family: -apple-system, BlinkMacSystemFont, \"Segoe UI\", \"PingFang SC\",\n            \"Hiragino Sans GB\", \"Microsoft YaHei\", Arial, sans-serif;\n        }\n        a {\n            color: #2563eb;\n            text-decoration: underline;\n        }\n        @media screen and (max-width: 600px) {\n            .container {\n                width: 100% !important;\n            }\n            .mobile-padding {\n                padding-left: 28px !important;\n                padding-right: 28px !important;\n            }\n            .code {\n                font-size: 48px !important;\n                letter-spacing: 3px !important;\n            }\n            .title {\n                font-size: 22px !important;\n            }\n        }\n    </style>\n</head>\n<body style=\"margin:0; padding:0; background-color:#ffffff;\">\n<div style=\"display:none; max-height:0; overflow:hidden; opacity:0; color:transparent; line-height:1px; font-size:1px;\">\n    æ‚¨æ­£åœ¨åˆ›å»ºç§æœ‰äº‘ç½‘ç›˜è´¦å·ï¼Œè¯·ä½¿ç”¨æ­¤éªŒè¯ç å®ŒæˆéªŒè¯ã€‚\n</div>\n<table role=\"presentation\" width=\"100%\" cellspacing=\"0\" cellpadding=\"0\" border=\"0\" style=\"background-color:#ffffff;\">\n    <tr>\n        <td align=\"center\">\n            <table role=\"presentation\" class=\"container\" width=\"680\" cellspacing=\"0\" cellpadding=\"0\" border=\"0\"\n                   style=\"width:680px; max-width:680px; background-color:#ffffff;\">\n                <tr>\n                    <td class=\"mobile-padding\" style=\"padding:48px 56px 24px 56px;\">\n                        <table role=\"presentation\" cellspacing=\"0\" cellpadding=\"0\" border=\"0\">\n                            <tr>\n                                <td valign=\"middle\">\n                                    <div style=\"width:42px; height:42px; border-radius:12px; background-color:#0f2d73; color:#ffffff; font-size:15px; font-weight:700; line-height:42px; text-align:center;\">\n                                        PCD\n                                    </div>\n                                </td>\n                                <td valign=\"middle\" style=\"padding-left:12px;\">\n                                    <div style=\"font-size:21px; line-height:1.2; color:#0f2d73; font-weight:700;\">\n                                        ç§æœ‰äº‘ç½‘ç›˜\n                                    </div>\n                                    <div style=\"font-size:13px; line-height:1.5; color:#64748b; margin-top:2px;\">\n                                        Private Cloud Disk\n                                    </div>\n                                </td>\n                            </tr>\n                        </table>\n                    </td>\n                </tr>\n                <tr>\n                    <td class=\"mobile-padding\" style=\"padding:18px 56px 0 56px;\">\n                        <p style=\"margin:0 0 26px 0; font-size:17px; line-height:1.8; color:#1f2937;\">\n                            æ‚¨å¥½\n                            <strong style=\"font-weight:700; color:#111827;\">{{.Username}}</strong>ï¼\n                        </p>\n                        <p style=\"margin:0 0 30px 0; font-size:17px; line-height:1.8; color:#1f2937;\">\n                            æ„Ÿè°¢æ‚¨ä½¿ç”¨ç§æœ‰äº‘ç½‘ç›˜ã€‚æ‚¨æ­£åœ¨è¿›è¡Œ\n                            <strong style=\"font-weight:700; color:#111827;\">{{.PurposeText}}</strong>\n                            æ“ä½œï¼Œè¯·ä½¿ç”¨ä»¥ä¸‹ä¸€æ¬¡æ€§éªŒè¯ç å®ŒæˆéªŒè¯ï¼š\n                        </p>\n                        <div class=\"code\"\n                             style=\"font-family:Consolas, Monaco, \'Courier New\', monospace; font-size:56px; line-height:1.2; letter-spacing:4px; font-weight:500; color:#000000; margin:0 0 30px 0;\">\n                            {{.VerificationCode}}\n                        </div>\n                        <p style=\"margin:0 0 28px 0; font-size:16px; line-height:1.9; color:#374151;\">\n                            è¯·è¿”å›æµè§ˆå™¨æˆ–å®¢æˆ·ç«¯ï¼Œåœ¨éªŒè¯ç è¾“å…¥æ¡†ä¸­è¾“å…¥æ­¤éªŒè¯ç ã€‚\n                            æ­¤éªŒè¯ç å°†åœ¨\n                            <strong style=\"color:#111827;\">{{.ExpireMinutes}}</strong>\n                            åˆ†é’Ÿåå¤±æ•ˆã€‚\n                        </p>\n                        <p style=\"margin:0 0 30px 0; font-size:16px; line-height:1.9; color:#374151;\">\n                            å¦‚æœæ‚¨æ²¡æœ‰å‘èµ·æ­¤æ“ä½œï¼Œè¯·å¿½ç•¥æ­¤é‚®ä»¶ã€‚ä¸ºäº†ä¿éšœè´¦å·å®‰å…¨ï¼Œå»ºè®®æ‚¨åŠæ—¶æ£€æŸ¥è´¦å·ç™»å½•è®°å½•æˆ–ä¿®æ”¹å¯†ç ã€‚\n                        </p>\n                        <p style=\"margin:0 0 8px 0; font-size:16px; line-height:1.8; color:#374151;\">\n                            è°¢è°¢æ‚¨ï¼Œ\n                        </p>\n                        <p style=\"margin:0; font-size:16px; line-height:1.8; color:#374151;\">\n                            ç§æœ‰äº‘ç½‘ç›˜å›¢é˜Ÿ\n                        </p>\n                    </td>\n                </tr>\n                <tr>\n                    <td class=\"mobile-padding\" style=\"padding:38px 56px 0 56px;\">\n                        <div style=\"height:1px; background-color:#e5e7eb; line-height:1px; font-size:1px;\">&nbsp;</div>\n                    </td>\n                </tr>\n                <tr>\n                    <td class=\"mobile-padding\" style=\"padding:28px 56px 56px 56px;\">\n                        <p style=\"margin:0 0 16px 0; font-size:13px; line-height:1.8; color:#6b7280;\">\n                            æ­¤é‚®ä»¶å‘é€è‡³\n                            <span>{{.Email}}</span>ï¼Œ\n                            ç”¨äºé€šçŸ¥æ‚¨çš„ç§æœ‰äº‘ç½‘ç›˜è´¦å·æ­£åœ¨è¿›è¡Œå®‰å…¨éªŒè¯ã€‚\n                        </p>\n                        <p style=\"margin:0 0 8px 0; font-size:13px; line-height:1.8; color:#6b7280;\">\n                            &copy; {{.CurrentYear}} Private Cloud Disk. All rights reserved.\n                        </p>\n                        <p style=\"margin:0; font-size:13px; line-height:1.8; color:#6b7280;\">\n                            æ­¤é‚®ä»¶ç”±ç³»ç»Ÿè‡ªåŠ¨å‘é€ï¼Œè¯·å‹¿ç›´æ¥å›å¤ã€‚\n                            å¦‚éœ€å¸®åŠ©ï¼Œè¯·è”ç³»\n                            <a href=\"mailto:{{.SupportEmail}}\">{{.SupportEmail}}</a>\n                        </p>\n                    </td>\n                </tr>\n            </table>\n        </td>\n    </tr>\n</table>\n</body>\n</html>','[{\"desc\": \"ç”¨æˆ·å\", \"name\": \"Username\", \"type\": \"string\", \"required\": true}, {\"desc\": \"æ“ä½œç”¨é€”æ–‡æœ¬\", \"name\": \"PurposeText\", \"type\": \"string\", \"required\": true}, {\"desc\": \"éªŒè¯ç \", \"name\": \"VerificationCode\", \"type\": \"string\", \"required\": true}, {\"desc\": \"è¿‡æœŸåˆ†é’Ÿæ•°\", \"name\": \"ExpireMinutes\", \"type\": \"int\", \"required\": true}, {\"desc\": \"æ”¶ä»¶äººé‚®ç®±\", \"name\": \"Email\", \"type\": \"string\", \"required\": true}, {\"desc\": \"å½“å‰å¹´ä»½\", \"name\": \"CurrentYear\", \"type\": \"string\", \"required\": true}, {\"desc\": \"æ”¯æŒé‚®ç®±åœ°å€\", \"name\": \"SupportEmail\", \"type\": \"string\", \"required\": true}]',1,'2026-06-22 17:51:01','2026-06-22 17:51:52'),(3,'welcome_sms','æ¬¢è¿çŸ­ä¿¡','sms','zh-CN','','ã€ç§æœ‰äº‘ã€‘{{.userName}}ï¼Œæ¬¢è¿åŠ å…¥ç§æœ‰äº‘ï¼æ‚¨çš„ä¸“å±äº‘å­˜å‚¨å·²å°±ç»ªï¼Œç«‹å³ç™»å½•ä½“éªŒã€‚',NULL,'[{\"name\": \"userName\", \"type\": \"string\", \"required\": true}]',1,'2026-06-22 17:51:01','2026-06-22 17:51:01'),(4,'verification_sms','éªŒè¯ç çŸ­ä¿¡','sms','zh-CN','','ã€ç§æœ‰äº‘ã€‘æ‚¨çš„éªŒè¯ç æ˜¯ {{.code}}ï¼Œ{{.expireMinutes}} åˆ†é’Ÿå†…æœ‰æ•ˆã€‚å¦‚éæœ¬äººæ“ä½œï¼Œè¯·å¿½ç•¥ã€‚',NULL,'[{\"name\": \"code\", \"type\": \"string\", \"required\": true}, {\"name\": \"expireMinutes\", \"type\": \"int\", \"required\": true}]',1,'2026-06-22 17:51:01','2026-06-22 17:51:01'),(5,'share_notify','åˆ†äº«é€šçŸ¥','push','zh-CN','{{.senderName}} ä¸ä½ åˆ†äº«äº†æ–‡ä»¶','{{.senderName}} åˆ†äº«äº† {{.fileCount}} ä¸ªæ–‡ä»¶ç»™ä½ ',NULL,'[{\"name\": \"senderName\", \"type\": \"string\", \"required\": true}, {\"name\": \"fileCount\", \"type\": \"int\", \"required\": true}]',1,'2026-06-22 17:51:01','2026-06-22 17:51:01'),(6,'system_notify','ç³»ç»Ÿé€šçŸ¥','push','zh-CN','ç³»ç»Ÿé€šçŸ¥','{{.message}}',NULL,'[{\"name\": \"message\", \"type\": \"string\", \"required\": true}]',1,'2026-06-22 17:51:01','2026-06-22 17:51:01'),(7,'welcome_email','Welcome Email','email','en-US','Welcome to Private Cloud Disk','Welcome to Private Cloud Disk! Your account has been created successfully.','<!DOCTYPE html>\n<html lang=\"en\">\n<head>\n    <meta charset=\"UTF-8\">\n    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n    <title>Welcome to Private Cloud Disk</title>\n    <style>\n        body, table, td, a { -webkit-text-size-adjust: 100%; -ms-text-size-adjust: 100%; }\n        table, td { mso-table-lspace: 0pt; mso-table-rspace: 0pt; }\n        body {\n            margin: 0 !important; padding: 0 !important; width: 100% !important;\n            background-color: #f3f5f8;\n            font-family: -apple-system, BlinkMacSystemFont, \"Segoe UI\", Arial, sans-serif;\n        }\n        a { color: #2563eb; text-decoration: none; }\n        @media screen and (max-width: 600px) {\n            .container { width: 100% !important; }\n            .mobile-padding { padding-left: 22px !important; padding-right: 22px !important; }\n        }\n    </style>\n</head>\n<body style=\"margin:0; padding:0; background-color:#f3f5f8;\">\n<div style=\"display:none; max-height:0; overflow:hidden; opacity:0; color:transparent; line-height:1px; font-size:1px;\">\n    Welcome to Private Cloud Disk, your account has been created.\n</div>\n<table role=\"presentation\" width=\"100%\" cellspacing=\"0\" cellpadding=\"0\" border=\"0\" style=\"background-color:#f3f5f8;\">\n    <tr>\n        <td align=\"center\" style=\"padding:32px 12px;\">\n            <table role=\"presentation\" class=\"container\" width=\"600\" cellspacing=\"0\" cellpadding=\"0\" border=\"0\"\n                   style=\"width:600px; max-width:600px; background-color:#ffffff; border-radius:18px; overflow:hidden; border:1px solid #e5e7eb;\">\n                <tr>\n                    <td class=\"mobile-padding\" style=\"padding:30px 36px 22px 36px; background-color:#ffffff; border-bottom:1px solid #eef0f4;\">\n                        <div style=\"font-size:16px; font-weight:700; color:#111827;\">Private Cloud Disk</div>\n                        <div style=\"font-size:12px; color:#6b7280;\">Secure File Storage Service</div>\n                    </td>\n                </tr>\n                <tr>\n                    <td class=\"mobile-padding\" style=\"padding:38px 36px 18px 36px;\">\n                        <div style=\"display:inline-block; padding:6px 12px; background-color:#ecfdf5; border:1px solid #bbf7d0; border-radius:999px; color:#047857; font-size:13px; font-weight:600;\">\n                            Registration Successful\n                        </div>\n                        <h1 style=\"margin:18px 0 12px 0; font-size:26px; line-height:1.35; color:#111827; font-weight:700;\">\n                            Welcome to Private Cloud Disk\n                        </h1>\n                        <p style=\"margin:0; font-size:14px; line-height:1.85; color:#4b5563;\">\n                            Hello {{.Username}},\n                            your account has been created successfully. You can now upload, manage, and access your files.\n                        </p>\n                    </td>\n                </tr>\n                <tr>\n                    <td class=\"mobile-padding\" style=\"padding:12px 36px 10px 36px;\">\n                        <table role=\"presentation\" width=\"100%\" cellspacing=\"0\" cellpadding=\"0\" border=\"0\"\n                               style=\"background-color:#f9fafb; border:1px solid #e5e7eb; border-radius:16px;\">\n                            <tr>\n                                <td style=\"padding:20px;\">\n                                    <div style=\"font-size:14px; font-weight:700; color:#111827; margin-bottom:12px;\">Account Information</div>\n                                    <table role=\"presentation\" width=\"100%\" cellspacing=\"0\" cellpadding=\"0\" border=\"0\">\n                                        <tr>\n                                            <td style=\"padding:8px 0; font-size:13px; color:#6b7280; width:90px;\">Email</td>\n                                            <td style=\"padding:8px 0; font-size:13px; color:#111827; font-weight:600;\">{{.Email}}</td>\n                                        </tr>\n                                        <tr>\n                                            <td style=\"padding:8px 0; font-size:13px; color:#6b7280;\">Registered</td>\n                                            <td style=\"padding:8px 0; font-size:13px; color:#111827; font-weight:600;\">{{.RegisterTime}}</td>\n                                        </tr>\n                                        <tr>\n                                            <td style=\"padding:8px 0; font-size:13px; color:#6b7280;\">Status</td>\n                                            <td style=\"padding:8px 0; font-size:13px; color:#047857; font-weight:700;\">Active</td>\n                                        </tr>\n                                    </table>\n                                </td>\n                            </tr>\n                        </table>\n                    </td>\n                </tr>\n                <tr>\n                    <td class=\"mobile-padding\" align=\"center\" style=\"padding:22px 36px 12px 36px;\">\n                        <a href=\"{{.LoginUrl}}\" target=\"_blank\"\n                           style=\"display:inline-block; background-color:#111827; color:#ffffff; font-size:14px; font-weight:700; padding:15px 28px; border-radius:12px; text-align:center; text-decoration:none;\">\n                            Go to Private Cloud Disk\n                        </a>\n                    </td>\n                </tr>\n                <tr>\n                    <td style=\"padding:22px 36px; background-color:#f9fafb; border-top:1px solid #eef0f4; text-align:center;\">\n                        <div style=\"font-size:13px; color:#111827; font-weight:700;\">Private Cloud Disk</div>\n                        <div style=\"font-size:11px; color:#9ca3af; margin-top:8px;\">&copy; {{.CurrentYear}} Private Cloud Disk. All rights reserved.</div>\n                    </td>\n                </tr>\n            </table>\n        </td>\n    </tr>\n</table>\n</body>\n</html>','[{\"desc\": \"User display name\", \"name\": \"Username\", \"type\": \"string\", \"required\": true}, {\"desc\": \"User email\", \"name\": \"Email\", \"type\": \"string\", \"required\": true}, {\"desc\": \"Registration time\", \"name\": \"RegisterTime\", \"type\": \"string\", \"required\": true}, {\"desc\": \"Login page URL\", \"name\": \"LoginUrl\", \"type\": \"string\", \"required\": true}, {\"desc\": \"Current year\", \"name\": \"CurrentYear\", \"type\": \"string\", \"required\": true}]',1,'2026-06-22 17:51:01','2026-06-22 17:52:01'),(8,'verification_sms','Verification SMS','sms','en-US','','[PrivateCloud] Your verification code is {{.code}}, valid for {{.expireMinutes}} minutes.',NULL,'[{\"name\": \"code\", \"type\": \"string\", \"required\": true}, {\"name\": \"expireMinutes\", \"type\": \"int\", \"required\": true}]',1,'2026-06-22 17:51:01','2026-06-22 17:51:01'),(9,'verification_email_dark','éªŒè¯ç é‚®ä»¶ï¼ˆæš—è‰²å®‰å…¨ç‰ˆï¼‰','email','zh-CN','å®‰å…¨éªŒè¯ç  - ç§æœ‰äº‘ç½‘ç›˜','æ‚¨æ­£åœ¨è¿›è¡Œæ•æ„Ÿå®‰å…¨æ“ä½œï¼ŒéªŒè¯ç ï¼š{{.VerificationCode}}ï¼Œæœ‰æ•ˆæœŸ {{.ExpireMinutes}} åˆ†é’Ÿã€‚','<!DOCTYPE html>\n<html lang=\"zh-CN\">\n<head>\n    <meta charset=\"UTF-8\">\n    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n    <title>å®‰å…¨éªŒè¯ç  - ç§æœ‰äº‘ç½‘ç›˜</title>\n    <style>\n        body, table, td, a {\n            -webkit-text-size-adjust: 100%;\n            -ms-text-size-adjust: 100%;\n        }\n        table, td {\n            mso-table-lspace: 0pt;\n            mso-table-rspace: 0pt;\n        }\n        img {\n            -ms-interpolation-mode: bicubic;\n            border: 0;\n            outline: none;\n            text-decoration: none;\n        }\n        body {\n            margin: 0 !important;\n            padding: 0 !important;\n            width: 100% !important;\n            background-color: #ffffff;\n            font-family: -apple-system, BlinkMacSystemFont, \"Segoe UI\", \"PingFang SC\",\n            \"Hiragino Sans GB\", \"Microsoft YaHei\", Arial, sans-serif;\n        }\n        a {\n            color: #60a5fa;\n            text-decoration: underline;\n        }\n        @media screen and (max-width: 600px) {\n            .container {\n                width: 100% !important;\n            }\n            .outer-padding {\n                padding: 24px 20px !important;\n            }\n            .card-padding {\n                padding-left: 24px !important;\n                padding-right: 24px !important;\n            }\n            .title {\n                font-size: 24px !important;\n            }\n            .code {\n                font-size: 42px !important;\n                letter-spacing: 3px !important;\n            }\n        }\n    </style>\n</head>\n<body style=\"margin:0; padding:0; background-color:#ffffff;\">\n<div style=\"display:none; max-height:0; overflow:hidden; opacity:0; color:transparent; line-height:1px; font-size:1px;\">\n    æ‚¨æ­£åœ¨è¿›è¡Œæ•æ„Ÿå®‰å…¨æ“ä½œï¼Œè¯·ä½¿ç”¨éªŒè¯ç ç¡®è®¤èº«ä»½ã€‚\n</div>\n<table role=\"presentation\" width=\"100%\" cellspacing=\"0\" cellpadding=\"0\" border=\"0\" style=\"background-color:#ffffff;\">\n    <tr>\n        <td align=\"center\" class=\"outer-padding\" style=\"padding:32px 24px;\">\n            <table role=\"presentation\" class=\"container\" width=\"720\" cellspacing=\"0\" cellpadding=\"0\" border=\"0\"\n                   style=\"width:720px; max-width:720px;\">\n                <tr>\n                    <td style=\"background-color:#1f232a; border-radius:0;\">\n                        <table role=\"presentation\" width=\"100%\" cellspacing=\"0\" cellpadding=\"0\" border=\"0\">\n                            <tr>\n                                <td class=\"card-padding\" style=\"padding:36px 36px 16px 36px;\">\n                                    <table role=\"presentation\" cellspacing=\"0\" cellpadding=\"0\" border=\"0\">\n                                        <tr>\n                                            <td valign=\"middle\">\n                                                <div style=\"width:46px; height:46px; border-radius:50%; background-color:#ffffff; color:#0f2d73; font-size:15px; font-weight:800; line-height:46px; text-align:center;\">\n                                                    PCD\n                                                </div>\n                                            </td>\n                                            <td valign=\"middle\" style=\"padding-left:14px;\">\n                                                <div style=\"font-size:18px; line-height:1.3; color:#ffffff; font-weight:700;\">\n                                                    ç§æœ‰äº‘ç½‘ç›˜\n                                                </div>\n                                                <div style=\"font-size:12px; line-height:1.5; color:#9ca3af;\">\n                                                    Private Cloud Disk Security\n                                                </div>\n                                            </td>\n                                        </tr>\n                                    </table>\n                                </td>\n                            </tr>\n                        </table>\n                        <table role=\"presentation\" width=\"100%\" cellspacing=\"0\" cellpadding=\"0\" border=\"0\">\n                            <tr>\n                                <td class=\"card-padding\" style=\"padding:34px 36px 0 36px;\">\n                                    <h1 class=\"title\" style=\"margin:0 0 26px 0; font-size:30px; line-height:1.35; color:#d1d5db; font-weight:700;\">\n                                        {{.Username}}ï¼Œæ‚¨å¥½ï¼\n                                    </h1>\n                                    <p style=\"margin:0 0 24px 0; font-size:22px; line-height:1.55; color:#ffffff; font-weight:700;\">\n                                        è¦å®Œæˆ\n                                        {{.PurposeText}}\n                                        æ“ä½œï¼Œè¯·å…ˆç¡®è®¤æ‚¨æ‹¥æœ‰æ­¤è´¦å·çš„è®¿é—®æƒé™ã€‚\n                                    </p>\n                                    <p style=\"margin:0 0 22px 0; font-size:15px; line-height:1.9; color:#d1d5db;\">\n                                        è¯·åœ¨æ‚¨çš„æµè§ˆå™¨æˆ–å®¢æˆ·ç«¯ä¸­è¾“å…¥ä»¥ä¸‹ä¸€æ¬¡æ€§éªŒè¯ç ï¼š\n                                    </p>\n                                </td>\n                            </tr>\n                        </table>\n                        <table role=\"presentation\" width=\"100%\" cellspacing=\"0\" cellpadding=\"0\" border=\"0\">\n                            <tr>\n                                <td class=\"card-padding\" style=\"padding:0 36px 28px 36px;\">\n                                    <table role=\"presentation\" width=\"100%\" cellspacing=\"0\" cellpadding=\"0\" border=\"0\"\n                                           style=\"background-color:#15191f;\">\n                                        <tr>\n                                            <td align=\"center\" style=\"padding:30px 18px;\">\n                                                <div class=\"code\"\n                                                     style=\"font-family:Consolas, Monaco, \'Courier New\', monospace; font-size:48px; line-height:1.2; letter-spacing:5px; color:#4da3ff; font-weight:800;\">\n                                                    {{.VerificationCode}}\n                                                </div>\n                                            </td>\n                                        </tr>\n                                    </table>\n                                </td>\n                            </tr>\n                        </table>\n                        <table role=\"presentation\" width=\"100%\" cellspacing=\"0\" cellpadding=\"0\" border=\"0\">\n                            <tr>\n                                <td class=\"card-padding\" style=\"padding:0 36px 26px 36px;\">\n                                    <p style=\"margin:0; font-size:15px; line-height:1.9; color:#d1d5db;\">\n                                        æ­¤éªŒè¯ç å°†åœ¨\n                                        <strong style=\"color:#ffffff;\">{{.ExpireMinutes}}</strong>\n                                        åˆ†é’Ÿåå¤±æ•ˆã€‚éªŒè¯ç ä»…ç”¨äºå½“å‰æ“ä½œï¼Œè¯·ä¸è¦è½¬å‘æˆ–é€éœ²ç»™ä»»ä½•äººã€‚\n                                    </p>\n                                </td>\n                            </tr>\n                        </table>\n                        <table role=\"presentation\" width=\"100%\" cellspacing=\"0\" cellpadding=\"0\" border=\"0\">\n                            <tr>\n                                <td class=\"card-padding\" style=\"padding:0 36px 34px 36px;\">\n                                    <table role=\"presentation\" width=\"100%\" cellspacing=\"0\" cellpadding=\"0\" border=\"0\"\n                                           style=\"background-color:#15191f;\">\n                                        <tr>\n                                            <td width=\"72\" align=\"center\" valign=\"middle\" style=\"padding:18px 0;\">\n                                                <div style=\"width:32px; height:32px; border-radius:50%; border:2px solid #d1d5db; color:#d1d5db; font-size:20px; line-height:30px; font-weight:700;\">\n                                                    i\n                                                </div>\n                                            </td>\n                                            <td valign=\"middle\" style=\"padding:18px 20px 18px 0;\">\n                                                <p style=\"margin:0; font-size:15px; line-height:1.8; color:#d1d5db;\">\n                                                    å¦‚æœæ‚¨æ²¡æœ‰å°è¯•è¿›è¡Œæ­¤æ“ä½œï¼Œè¯·ç«‹å³ä¿®æ”¹å¯†ç ï¼Œå¹¶æ£€æŸ¥è´¦å·å®‰å…¨è®¾ç½®ã€‚\n                                                </p>\n                                            </td>\n                                        </tr>\n                                    </table>\n                                </td>\n                            </tr>\n                        </table>\n                        <table role=\"presentation\" width=\"100%\" cellspacing=\"0\" cellpadding=\"0\" border=\"0\">\n                            <tr>\n                                <td class=\"card-padding\" style=\"padding:0 36px 42px 36px;\">\n                                    <table role=\"presentation\" cellspacing=\"0\" cellpadding=\"0\" border=\"0\">\n                                        <tr>\n                                            <td width=\"4\" style=\"background-color:#3b82f6; font-size:1px; line-height:1px;\">\n                                                &nbsp;\n                                            </td>\n                                            <td style=\"padding-left:28px;\">\n                                                <p style=\"margin:0 0 4px 0; font-size:15px; line-height:1.7; color:#d1d5db;\">\n                                                    ç¥æ‚¨ä½¿ç”¨æ„‰å¿«ï¼Œ\n                                                </p>\n                                                <p style=\"margin:0; font-size:15px; line-height:1.7; color:#ffffff;\">\n                                                    ç§æœ‰äº‘ç½‘ç›˜å®‰å…¨å›¢é˜Ÿ\n                                                </p>\n                                            </td>\n                                        </tr>\n                                    </table>\n                                </td>\n                            </tr>\n                        </table>\n                    </td>\n                </tr>\n                <tr>\n                    <td style=\"padding:30px 18px 0 18px;\">\n                        <p style=\"margin:0 0 18px 0; font-size:15px; line-height:1.9; color:#111827;\">\n                            æ­¤é€šçŸ¥å·²å‘é€è‡³ä¸æ‚¨çš„ç§æœ‰äº‘ç½‘ç›˜è´¦å·å…³è”çš„ç”µå­é‚®ä»¶åœ°å€ã€‚\n                        </p>\n                        <p style=\"margin:0 0 18px 0; font-size:15px; line-height:1.9; color:#111827;\">\n                            è¿™å°ç”µå­é‚®ä»¶ç”±ç³»ç»Ÿè‡ªåŠ¨ç”Ÿæˆï¼Œè¯·å‹¿å›å¤ã€‚\n                            å¦‚æœæ‚¨éœ€è¦é¢å¤–å¸®åŠ©ï¼Œè¯·è”ç³»ç®¡ç†å‘˜æˆ–è®¿é—®å¸®åŠ©ä¸­å¿ƒã€‚\n                        </p>\n                        <p style=\"margin:0; font-size:15px; line-height:1.9; color:#111827;\">\n                            <a href=\"{{.HelpUrl}}\" style=\"color:#111827; text-decoration:underline;\">å¸®åŠ©ä¸­å¿ƒ</a>\n                        </p>\n                    </td>\n                </tr>\n            </table>\n        </td>\n    </tr>\n</table>\n</body>\n</html>','[{\"desc\": \"ç”¨æˆ·å\", \"name\": \"Username\", \"type\": \"string\", \"required\": true}, {\"desc\": \"æ“ä½œç”¨é€”æ–‡æœ¬\", \"name\": \"PurposeText\", \"type\": \"string\", \"required\": true}, {\"desc\": \"éªŒè¯ç \", \"name\": \"VerificationCode\", \"type\": \"string\", \"required\": true}, {\"desc\": \"è¿‡æœŸåˆ†é’Ÿæ•°\", \"name\": \"ExpireMinutes\", \"type\": \"int\", \"required\": true}, {\"desc\": \"å¸®åŠ©ä¸­å¿ƒURL\", \"name\": \"HelpUrl\", \"type\": \"string\", \"required\": true}]',1,'2026-06-22 17:51:57','2026-06-22 17:51:57');
/*!40000 ALTER TABLE `pcd_notification_templates` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `pcd_recent_access_table`
--

DROP TABLE IF EXISTS `pcd_recent_access_table`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `pcd_recent_access_table` (
  `ra_id` bigint NOT NULL AUTO_INCREMENT COMMENT 'è®°å½•ID',
  `ra_user_id` binary(16) NOT NULL COMMENT 'ç”¨æˆ·ID',
  `ra_target_type` enum('file','folder') NOT NULL COMMENT 'ç›®æ ‡ç±»å‹',
  `ra_file_id` binary(16) DEFAULT NULL COMMENT 'æ–‡ä»¶ID',
  `ra_node_id` binary(16) DEFAULT NULL COMMENT 'æ–‡ä»¶å¤¹èŠ‚ç‚¹ID',
  `ra_access_type` enum('upload','download','open') NOT NULL COMMENT 'è®¿é—®ç±»å‹',
  `ra_file_name` varchar(255) NOT NULL COMMENT 'æ–‡ä»¶/æ–‡ä»¶å¤¹åç§°ï¼ˆå†—ä½™ï¼Œé¿å…JOINï¼‰',
  `ra_file_size` bigint NOT NULL DEFAULT '0' COMMENT 'æ–‡ä»¶å¤§å°ï¼ˆå†—ä½™ï¼‰',
  `ra_file_type` varchar(60) DEFAULT '' COMMENT 'æ–‡ä»¶ç±»å‹ï¼ˆå†—ä½™ï¼‰',
  `ra_accessed_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'è®¿é—®æ—¶é—´',
  PRIMARY KEY (`ra_id`),
  KEY `idx_ra_user_type_time` (`ra_user_id`,`ra_access_type`,`ra_accessed_at` DESC),
  KEY `idx_ra_user_time` (`ra_user_id`,`ra_accessed_at` DESC),
  CONSTRAINT `pcd_recent_access_table_ibfk_1` FOREIGN KEY (`ra_user_id`) REFERENCES `pcd_user_info_table` (`user_id`) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=141 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='æœ€è¿‘è®¿é—®è®°å½•è¡¨';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `pcd_recent_access_table`
--

LOCK TABLES `pcd_recent_access_table` WRITE;
/*!40000 ALTER TABLE `pcd_recent_access_table` DISABLE KEYS */;
INSERT INTO `pcd_recent_access_table` VALUES (39,_binary 'UUUUUUUUUUUUUUUU','file',_binary '\Åv\Zw’A¢.¸h4n\â\Ò',NULL,'upload','FileSearchControllerApi.md',1564,'text/markdown','2026-07-13 19:20:14'),(40,_binary 'UUUUUUUUUUUUUUUU','file',_binary '\ì„\çy\ÍIp¹}\Øgß´9\Ä',NULL,'upload','JsonResultListShareLinkVO.md',704,'text/markdown','2026-07-13 19:20:17'),(41,_binary 'UUUUUUUUUUUUUUUU','file',_binary '¢ü\íP,H±\Ì`‡ŒT',NULL,'upload','JsonResultListFileStarVO.md',699,'text/markdown','2026-07-13 19:20:19'),(42,_binary 'UUUUUUUUUUUUUUUU','file',_binary '\É3T\ÍVB,ƒjo\â¤g’ ',NULL,'upload','JsonResultShareLinkEntity.md',699,'text/markdown','2026-07-13 19:20:21'),(43,_binary 'UUUUUUUUUUUUUUUU','file',_binary '\İø2\ôLWCf›ºUB2­',NULL,'upload','JsonResultFileEntity.md',674,'text/markdown','2026-07-13 19:20:23'),(44,_binary 'UUUUUUUUUUUUUUUU','file',_binary '`}´{„\ğE€»\æ>`\êü*',NULL,'upload','RegisterUserRequest.md',865,'text/markdown','2026-07-13 19:20:25'),(45,_binary 'UUUUUUUUUUUUUUUU','file',_binary 'Ho\Î\äıDøƒ 7\×Yj0¼',NULL,'upload','JsonResultListShareContentItemVO.md',739,'text/markdown','2026-07-13 19:20:28'),(46,_binary 'UUUUUUUUUUUUUUUU','file',_binary '’G\Ó\ìJ$²›FaPº…\ï',NULL,'upload','FileControllerApi.md',11702,'text/markdown','2026-07-13 19:20:30'),(47,_binary 'UUUUUUUUUUUUUUUU','file',_binary '\Æ2\äL\ô\ñL™\0\Ü$Ü´\Ã',NULL,'upload','JsonResultVoid.md',635,'text/markdown','2026-07-13 19:20:32'),(48,_binary 'UUUUUUUUUUUUUUUU','file',_binary '\æ°`;ŸN×¶YˆÅ»m\Ù',NULL,'upload','UploadsSessionInternalVO.md',1446,'text/markdown','2026-07-13 19:20:34'),(49,_binary 'UUUUUUUUUUUUUUUU','file',_binary 'a#/w£/Jµ™Ÿ_7vs9',NULL,'upload','PageResultVONodeVO.md',833,'text/markdown','2026-07-13 19:20:36'),(50,_binary 'UUUUUUUUUUUUUUUU','file',_binary '¸T\òƒ˜tNG¾\İ\Ø`\Ì\ÈÀ–',NULL,'upload','MoveFileRequest.md',499,'text/markdown','2026-07-13 19:20:38'),(51,_binary 'UUUUUUUUUUUUUUUU','file',_binary '\à\ês3ceLÊ\à\ó^¼B',NULL,'upload','JsonResultQuotaVO.md',659,'text/markdown','2026-07-13 19:20:41'),(52,_binary 'UUUUUUUUUUUUUUUU','file',_binary '\÷\ô\Ï\0l²Mo-\ì?d\Ê\İ',NULL,'upload','JsonResultBoolean.md',645,'text/markdown','2026-07-13 19:20:43'),(53,_binary 'UUUUUUUUUUUUUUUU','file',_binary '\äG9^\÷OÊº½š‰\0\èz',NULL,'upload','VerificationSendRequest.md',917,'text/markdown','2026-07-13 19:20:45'),(54,_binary 'UUUUUUUUUUUUUUUU','file',_binary '\\]\ô;ÇB\áš[\Â`²¢r',NULL,'upload','JsonResultInteger.md',644,'text/markdown','2026-07-13 19:20:47'),(55,_binary 'UUUUUUUUUUUUUUUU','file',_binary '`\Ô\Ö:=\êCŒ§D^\İ\åÿy',NULL,'upload','LoginRequest.md',822,'text/markdown','2026-07-13 19:20:49'),(56,_binary 'UUUUUUUUUUUUUUUU','file',_binary '{g%§F†Œ9\å’P4\ğ',NULL,'upload','JsonResultListTrashTargetVO.md',714,'text/markdown','2026-07-13 19:20:52'),(57,_binary 'UUUUUUUUUUUUUUUU','file',_binary '\Å\Ødb¯N€½e¬ƒ\Zll',NULL,'upload','JsonResultListNodeVO.md',679,'text/markdown','2026-07-13 19:20:54'),(58,_binary 'UUUUUUUUUUUUUUUU','file',_binary '»ˆ/4\ã›Is…Zû·Å“',NULL,'upload','LoginDeviceVO.md',905,'text/markdown','2026-07-13 19:20:56'),(59,_binary 'UUUUUUUUUUUUUUUU','file',_binary '9U>ú·K”¿c¼À„h',NULL,'upload','JsonResultPageResultVONodeVO.md',714,'text/markdown','2026-07-13 19:20:58'),(60,_binary 'UUUUUUUUUUUUUUUU','file',_binary '\Z•5Ÿ\ÍoJÚ­\0>s»\ÄV',NULL,'upload','FileVO.md',925,'text/markdown','2026-07-13 19:21:00'),(61,_binary 'UUUUUUUUUUUUUUUU','file',_binary '\àºN½	Ccˆ\Ú\ŞY;\õY\n',NULL,'upload','MoveNodeRequest.md',501,'text/markdown','2026-07-13 19:21:03'),(62,_binary 'UUUUUUUUUUUUUUUU','file',_binary '\÷”\ê7©L\ã™ş\ğ¡\Èg\Ø',NULL,'upload','NodeControllerApi.md',19343,'text/markdown','2026-07-13 19:21:05'),(63,_binary 'UUUUUUUUUUUUUUUU','file',_binary '§“S›AOº\ÆI†z^\Z\n',NULL,'upload','TrashControllerApi.md',14951,'text/markdown','2026-07-13 19:21:07'),(64,_binary 'UUUUUUUUUUUUUUUU','file',_binary 'Ÿy`OsDÒš(Œ\ÅD·0',NULL,'upload','CreateFolderNodeRequest.md',583,'text/markdown','2026-07-13 19:21:10'),(65,_binary 'UUUUUUUUUUUUUUUU','file',_binary '³4Ct)@p²\rV\"*I˜',NULL,'upload','ShareContentItemVO.md',886,'text/markdown','2026-07-13 19:21:12'),(66,_binary 'UUUUUUUUUUUUUUUU','file',_binary '\É\ê\ğ\Õ\Û)C¨‰—c$\Ğ',NULL,'upload','FileSearchRequest.md',1312,'text/markdown','2026-07-13 19:21:14'),(67,_binary 'UUUUUUUUUUUUUUUU','file',_binary '\ãx{‡nI>®\Í[\ç\ñ*[',NULL,'upload','FolderFileInfo.md',734,'text/markdown','2026-07-13 19:21:16'),(68,_binary 'UUUUUUUUUUUUUUUU','file',_binary '\ê\ß&\è¼nMÕ¸üp\"$«V',NULL,'upload','UserProfileVO.md',871,'text/markdown','2026-07-13 19:21:19'),(69,_binary 'UUUUUUUUUUUUUUUU','file',_binary 'u6ú/NP›\Ê\Ë2pnßŒ',NULL,'upload','UpdateUserInfoRequest.md',659,'text/markdown','2026-07-13 19:21:21'),(70,_binary 'UUUUUUUUUUUUUUUU','file',_binary 'Ì\Ùoı\ÚJÕ‹…´\n«\õ~V',NULL,'upload','UserControllerApi.md',10711,'text/markdown','2026-07-13 19:21:23'),(71,_binary 'UUUUUUUUUUUUUUUU','file',_binary 'Â¸)\Ï\ÉHØµ\0ú\åX¹O',NULL,'upload','JsonResultFileVO.md',654,'text/markdown','2026-07-13 19:21:25'),(72,_binary 'UUUUUUUUUUUUUUUU','file',_binary '	©\r´À=H¤š#0œ«{',NULL,'upload','QuotaVO.md',978,'text/markdown','2026-07-13 19:21:27'),(73,_binary 'UUUUUUUUUUUUUUUU','file',_binary 'TÜ\0-†Iw¾øGM‰ß¤',NULL,'upload','UploadsChunkInternalVO.md',895,'text/markdown','2026-07-13 19:21:30'),(74,_binary 'UUUUUUUUUUUUUUUU','file',_binary '¾:‰À¸TK‚¸»G\â\å\âü“',NULL,'upload','JsonResultListString.md',666,'text/markdown','2026-07-13 19:21:32'),(75,_binary 'UUUUUUUUUUUUUUUU','file',_binary '\ÂVİ³\òzFP¾¤mDl°',NULL,'upload','JsonResultTrashTargetVO.md',689,'text/markdown','2026-07-13 19:21:34'),(76,_binary 'UUUUUUUUUUUUUUUU','file',_binary 'R•\ö»~`O”‹J\Ï\à\'a',NULL,'upload','ShareLinkEntity.md',2024,'text/markdown','2026-07-13 19:21:36'),(77,_binary 'UUUUUUUUUUUUUUUU','file',_binary 'ùy?3\äG/–«\ğ‘Æ­\Ü',NULL,'upload','RenameNodeRequest.md',503,'text/markdown','2026-07-13 19:21:38'),(78,_binary 'UUUUUUUUUUUUUUUU','file',_binary 'H3½´\İDŒ¥\n\ñ£,€²',NULL,'upload','ShareAccessInfoVO.md',1446,'text/markdown','2026-07-13 19:21:41'),(79,_binary 'UUUUUUUUUUUUUUUU','file',_binary '/i$)\ìƒN™¬ù\ß#­U³',NULL,'upload','FileStarControllerApi.md',13561,'text/markdown','2026-07-13 19:21:43'),(80,_binary 'UUUUUUUUUUUUUUUU','file',_binary '\Ö}Ş„NB \ÌtB\Z\ô±\È',NULL,'upload','JsonResultFileSearchVO.md',684,'text/markdown','2026-07-13 19:21:45'),(81,_binary 'UUUUUUUUUUUUUUUU','file',_binary '‘8ZJƒM¯£®Nª{-“',NULL,'upload','CreateUploadsSessionRequest.md',967,'text/markdown','2026-07-13 19:21:47'),(82,_binary 'UUUUUUUUUUUUUUUU','file',_binary '5™€bLy“¼\õ\Ü\ô',NULL,'upload','VerificationSendVO.md',689,'text/markdown','2026-07-13 19:21:50'),(83,_binary 'UUUUUUUUUUUUUUUU','file',_binary '4\é³\à\×IW¯nªL1\r\ç	',NULL,'upload','UploadsControllerApi.md',3045,'text/markdown','2026-07-13 19:21:52'),(84,_binary 'UUUUUUUUUUUUUUUU','file',_binary 'ÿÚº\ô´RJKƒ\åsEQ@ ',NULL,'upload','FileSearchVO.md',799,'text/markdown','2026-07-13 19:21:54'),(85,_binary 'UUUUUUUUUUUUUUUU','file',_binary '‡\Ú\ÂOGª¿Ô¦¾¸',NULL,'upload','ShareCreateRequest.md',889,'text/markdown','2026-07-13 19:21:56'),(86,_binary 'UUUUUUUUUUUUUUUU','file',_binary '4#TF+¢%Ÿ`\Ù/Q',NULL,'upload','UploadAvatarRequest.md',493,'text/markdown','2026-07-13 19:21:58'),(87,_binary 'UUUUUUUUUUUUUUUU','file',_binary 'rpÌ¨M-@&³E	\ì',NULL,'upload','JsonResultInternalFileMetadataVO.md',734,'text/markdown','2026-07-13 19:22:01'),(88,_binary 'UUUUUUUUUUUUUUUU','file',_binary '\æt°n@p²6ƒÊ‡”)B',NULL,'download','JsonResultFolderNodeVO.md',684,'','2026-07-13 19:23:14'),(89,_binary 'UUUUUUUUUUUUUUUU','file',_binary '\Æ5­$K\ãEF©\İ%\Ü>r',NULL,'download','.DS_Store',6148,'','2026-07-13 23:20:17'),(90,_binary 'UUUUUUUUUUUUUUUU','file',_binary 'à¶©\0Y‹C¦³¤ü\î\ÈŞ½',NULL,'upload','tox.ini',149,'application/octet-stream','2026-07-13 23:33:14'),(91,_binary 'UUUUUUUUUUUUUUUU','file',_binary '\Ü#\ô¤?\òA{±PF!†ƒm',NULL,'upload','ä½¿ç”¨è¯´æ˜æ–‡æ¡£.txt',437,'text/plain','2026-07-13 23:35:10'),(92,_binary 'UUUUUUUUUUUUUUUU','file',_binary '=\ğe¼³A9¨r¬Î“>£',NULL,'upload','ä¸‰ç§æ–¹å¼é€‰æ‹©å…¶ä¸­ä¸€ç§å³å¯.txt',0,'text/plain','2026-07-13 23:35:12'),(93,_binary 'UUUUUUUUUUUUUUUU','file',_binary 'ınˆ\İKB“»R²yg',NULL,'upload','.DS_Store',6148,'application/octet-stream','2026-07-13 23:35:15'),(94,_binary 'UUUUUUUUUUUUUUUU','file',_binary '¬\Æ.\ëB±\r¤L@‚\Î)',NULL,'upload','æ³¨æ„ï¼šå°†æ–‡ä»¶å¤¹æ•´ä¸ªæ‹·è´åˆ°æŸä¸ªä½ç½®ï¼Œå°±ä¸è¦åŠ¨äº†.txt',0,'text/plain','2026-07-13 23:35:17'),(95,_binary 'UUUUUUUUUUUUUUUU','file',_binary 'O;k\×\æDœ±\ÖG§¾WT',NULL,'upload','ja-netfilter.jar',48642,'application/java-archive','2026-07-13 23:35:21'),(96,_binary 'UUUUUUUUUUUUUUUU','file',_binary '`\0\ôÿ#WE:‹\ĞqŠ\"\ÉT±',NULL,'upload','readme.txt',504,'text/plain','2026-07-13 23:35:23'),(97,_binary 'UUUUUUUUUUUUUUUU','file',_binary '\Ú\È\ÛE3¢h\ç¥şW\à\Ë',NULL,'upload','dns.jar',4859,'application/java-archive','2026-07-13 23:35:25'),(98,_binary 'UUUUUUUUUUUUUUUU','file',_binary '¶Uÿe5¸Of­°\à oş\è\Ç',NULL,'upload','power.jar',9222,'application/java-archive','2026-07-13 23:35:28'),(99,_binary 'UUUUUUUUUUUUUUUU','file',_binary 'p\Ï5ŸH “\÷\Ê|\Õ¨',NULL,'upload','hideme.jar',7209,'application/java-archive','2026-07-13 23:35:30'),(100,_binary 'UUUUUUUUUUUUUUUU','file',_binary 'U$¶¬<\ÈGÀ¯Å£¼S:\Ò',NULL,'upload','url.jar',4529,'application/java-archive','2026-07-13 23:35:33'),(101,_binary 'UUUUUUUUUUUUUUUU','file',_binary ' g\ÅÀ%TIø.K¤`Ö£\Õ',NULL,'upload','dns.conf',67,'application/octet-stream','2026-07-13 23:35:36'),(102,_binary 'UUUUUUUUUUUUUUUU','file',_binary '\r\î†t\ĞIº†˜yX\Ç',NULL,'upload','power.conf',7487,'application/octet-stream','2026-07-13 23:35:38'),(103,_binary 'UUUUUUUUUUUUUUUU','file',_binary 'DµLÇµMhºWp±»',NULL,'upload','mymap.conf',97,'application/octet-stream','2026-07-13 23:35:41'),(104,_binary 'UUUUUUUUUUUUUUUU','file',_binary '\í \×E¿\ÃN—42:Á¥2',NULL,'upload','url.conf',74,'application/octet-stream','2026-07-13 23:35:43'),(105,_binary 'UUUUUUUUUUUUUUUU','file',_binary 'Ù®CeıvBÏ®ƒ¸Å†\÷¤l',NULL,'upload','.DS_Store',6148,'application/octet-stream','2026-07-13 23:35:45'),(106,_binary 'UUUUUUUUUUUUUUUU','file',_binary 'TR\×h™J#­Ñ€6Z0	',NULL,'upload','.DS_Store',10244,'application/octet-stream','2026-07-13 23:35:48'),(107,_binary 'UUUUUUUUUUUUUUUU','file',_binary '˜\ê~\İ\òK\ò‹x\ö…\Ü',NULL,'upload','ja-netfilter.jar',48639,'application/java-archive','2026-07-13 23:35:50'),(108,_binary 'UUUUUUUUUUUUUUUU','file',_binary ']\ô^v1H£«e	\Õ	y',NULL,'upload','dns.conf',49,'application/octet-stream','2026-07-13 23:35:53'),(109,_binary 'UUUUUUUUUUUUUUUU','file',_binary 'Ç°\í\0*HSˆª\ñ·pT\İ\Ä',NULL,'upload','power.conf',6854,'application/octet-stream','2026-07-13 23:35:56'),(110,_binary 'UUUUUUUUUUUUUUUU','file',_binary '–?\ĞLVI\òŸEc\èV\Ï\Ç\ñ',NULL,'upload','url.conf',74,'application/octet-stream','2026-07-13 23:35:59'),(111,_binary 'UUUUUUUUUUUUUUUU','file',_binary '®|B¥˜³FÊ“\0x\çù&‘',NULL,'upload','install-all-users.vbs',2426,'application/octet-stream','2026-07-13 23:36:02'),(112,_binary 'UUUUUUUUUUUUUUUU','file',_binary '\ñ\0B>\Ì\ÆF#^Z”\ÌNn',NULL,'upload','uninstall-all-users.vbs',1065,'application/octet-stream','2026-07-13 23:36:04'),(113,_binary 'UUUUUUUUUUUUUUUU','file',_binary 'l\ì5\ÂYK=¸Œ‰\îR',NULL,'upload','uninstall.sh',1805,'text/x-sh','2026-07-13 23:36:07'),(114,_binary 'UUUUUUUUUUUUUUUU','file',_binary '\òş„¬k+FÔ³q\ñ»ş\ğez',NULL,'upload','uninstall-current-user.vbs',749,'application/octet-stream','2026-07-13 23:36:10'),(115,_binary 'UUUUUUUUUUUUUUUU','file',_binary 'µm¥¸„]G…®˜œ£¿\ÃÆ—',NULL,'upload','install.sh',3498,'text/x-sh','2026-07-13 23:36:14'),(116,_binary 'UUUUUUUUUUUUUUUU','file',_binary '¤\ïLÖ»@ğ·‘Œ¸±$04',NULL,'upload','install-current-user.vbs',1811,'application/octet-stream','2026-07-13 23:36:16'),(117,_binary 'UUUUUUUUUUUUUUUU','file',_binary '(\Ø\Ë8›\ÆMÑ·qÁ\â',NULL,'upload','webstorm.vmoptions',635,'application/octet-stream','2026-07-13 23:36:19'),(118,_binary 'UUUUUUUUUUUUUUUU','file',_binary 'e\áRp¬Fpº	$À\ñ±:\ï',NULL,'upload','clion.vmoptions',635,'application/octet-stream','2026-07-13 23:36:21'),(119,_binary 'UUUUUUUUUUUUUUUU','file',_binary 'b![^O|¨úh½\ÇCa',NULL,'upload','appcode.vmoptions',635,'application/octet-stream','2026-07-13 23:36:25'),(120,_binary 'UUUUUUUUUUUUUUUU','file',_binary '\ĞN\â¤leHã¦&’\öà§’',NULL,'upload','pycharm.vmoptions',635,'application/octet-stream','2026-07-13 23:36:27'),(121,_binary 'UUUUUUUUUUUUUUUU','file',_binary '™£†H¢@¸‹N‡\òxù',NULL,'upload','rubymine.vmoptions',635,'application/octet-stream','2026-07-13 23:36:30'),(122,_binary 'UUUUUUUUUUUUUUUU','file',_binary '.šQ>euLÎŒ\ñ\ïW\İ',NULL,'upload','idea.vmoptions',635,'application/octet-stream','2026-07-13 23:36:34'),(123,_binary 'UUUUUUUUUUUUUUUU','file',_binary '\ñ”1:.I\ò \÷\á\0¶‰\Ú',NULL,'upload','studio.vmoptions',635,'application/octet-stream','2026-07-13 23:36:38'),(124,_binary 'UUUUUUUUUUUUUUUU','file',_binary '\ê^»’5\ßDÙ„\èş\Ñ\Í\Ï\ç·',NULL,'upload','devecostudio.vmoptions',634,'application/octet-stream','2026-07-13 23:36:43'),(125,_binary 'UUUUUUUUUUUUUUUU','file',_binary '!\ä‹|‚C?\Õ;/\Ôfª',NULL,'upload','dataspell.vmoptions',634,'application/octet-stream','2026-07-13 23:36:47'),(126,_binary 'UUUUUUUUUUUUUUUU','file',_binary 'Uf_a·\ÄH6‘\Ë\Ü\Üm”\0',NULL,'upload','webide.vmoptions',635,'application/octet-stream','2026-07-13 23:36:48'),(127,_binary 'UUUUUUUUUUUUUUUU','file',_binary ',QŒ\ô\ñE’Tœ\Ë\ödK',NULL,'upload','gateway.vmoptions',634,'application/octet-stream','2026-07-13 23:36:51'),(128,_binary 'UUUUUUUUUUUUUUUU','file',_binary '\É5–\âUSH\ï¡l€»h\ß',NULL,'upload','jetbrains_client.vmoptions',635,'application/octet-stream','2026-07-13 23:36:53'),(129,_binary 'UUUUUUUUUUUUUUUU','file',_binary '¹[\Ò-\Ò\"OqŒ@\Ò\ô\èg',NULL,'upload','datagrip.vmoptions',634,'application/octet-stream','2026-07-13 23:36:56'),(130,_binary 'UUUUUUUUUUUUUUUU','file',_binary 'ú^TSvCTª®K{T\Û;ù',NULL,'upload','goland.vmoptions',634,'application/octet-stream','2026-07-13 23:36:59'),(131,_binary 'UUUUUUUUUUUUUUUU','file',_binary 'ªn _KÁ–\Â²šG',NULL,'upload','jetbrainsclient.vmoptions',636,'application/octet-stream','2026-07-13 23:37:01'),(132,_binary 'UUUUUUUUUUUUUUUU','file',_binary '|\Öq‡\ÚGË²’>²RŸn',NULL,'upload','rider.vmoptions',635,'application/octet-stream','2026-07-13 23:37:04'),(133,_binary 'UUUUUUUUUUUUUUUU','file',_binary 'F‹W›dI@¦)\ØH«Zg',NULL,'upload','phpstorm.vmoptions',635,'application/octet-stream','2026-07-13 23:37:06'),(134,_binary 'UUUUUUUUUUUUUUUU','file',_binary '¨E¾¹ÿTN6¿\Üq\íx2',NULL,'upload','dns.jar',4859,'application/java-archive','2026-07-13 23:37:08'),(135,_binary 'UUUUUUUUUUUUUUUU','file',_binary '\æ\rXm$G\î¦\é>YS\n',NULL,'upload','power.jar',9222,'application/java-archive','2026-07-13 23:37:11'),(136,_binary 'UUUUUUUUUUUUUUUU','file',_binary '+®a\ôûMAl¾›Jm\õH‡',NULL,'upload','hideme.jar',7209,'application/java-archive','2026-07-13 23:37:14'),(137,_binary 'UUUUUUUUUUUUUUUU','file',_binary ')\ØÜ§\Ä\ÈDK½\æ\æMR}',NULL,'upload','url.jar',4529,'application/java-archive','2026-07-13 23:37:17'),(138,_binary 'UUUUUUUUUUUUUUUU','file',_binary '\ß\ôPaĞŸAŸ,Mcgşa',NULL,'upload','ide-eval-resetter-2.1.6.zip',30838,'application/zip','2026-07-13 23:37:20'),(139,_binary 'UUUUUUUUUUUUUUUU','file',_binary '\ã“7È®\êC»¤¦\Ø\"\İy',NULL,'upload','reset_jetbrains_eval_windows.vbs',1019,'application/octet-stream','2026-07-13 23:37:22'),(140,_binary 'UUUUUUUUUUUUUUUU','file',_binary '\æŒì½¢IKq±D±^\Ä2©',NULL,'upload','reset_jetbrains_eval_mac_linux.sh',558,'text/x-sh','2026-07-13 23:37:24');
/*!40000 ALTER TABLE `pcd_recent_access_table` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `pcd_share_link_table`
--

DROP TABLE IF EXISTS `pcd_share_link_table`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `pcd_share_link_table` (
  `share_id` binary(16) NOT NULL COMMENT 'åˆ†äº«IDï¼ˆå†…éƒ¨ä¸»é”®ï¼‰',
  `share_token` varchar(36) NOT NULL COMMENT 'åˆ†äº«è®¿é—®ä»¤ç‰Œï¼ˆUUIDï¼Œå¯¹å¤–æš´éœ²ï¼‰',
  `share_owner_id` binary(16) NOT NULL COMMENT 'åˆ†äº«è€…ç”¨æˆ·ID',
  `share_target_type` enum('file','folder') NOT NULL COMMENT 'åˆ†äº«ç›®æ ‡ç±»å‹',
  `share_file_id` binary(16) DEFAULT NULL COMMENT 'åˆ†äº«çš„æ–‡ä»¶IDï¼ˆåˆ†äº«æ–‡ä»¶æ—¶å¡«å†™ï¼‰',
  `share_node_id` binary(16) DEFAULT NULL COMMENT 'åˆ†äº«çš„æ–‡ä»¶å¤¹èŠ‚ç‚¹IDï¼ˆåˆ†äº«æ–‡ä»¶å¤¹æ—¶å¡«å†™ï¼‰',
  `share_name` varchar(200) NOT NULL COMMENT 'åˆ†äº«åç§°ï¼ˆç”¨æˆ·è‡ªå®šä¹‰ï¼‰',
  `share_password` varchar(120) DEFAULT NULL COMMENT 'æå–ç ï¼ˆBCrypt å“ˆå¸Œï¼ŒNULL è¡¨ç¤ºæ— å¯†ç ï¼‰',
  `share_has_password` tinyint(1) NOT NULL DEFAULT '0' COMMENT 'æ˜¯å¦æœ‰å¯†ç ä¿æŠ¤',
  `share_expires_at` datetime DEFAULT NULL COMMENT 'è¿‡æœŸæ—¶é—´ï¼ˆNULL è¡¨ç¤ºæ°¸ä¹…æœ‰æ•ˆï¼‰',
  `share_view_count` int NOT NULL DEFAULT '0' COMMENT 'æµè§ˆæ¬¡æ•°',
  `share_status` enum('active','revoked','expired') NOT NULL DEFAULT 'active' COMMENT 'åˆ†äº«çŠ¶æ€',
  `share_created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'åˆ›å»ºæ—¶é—´',
  `share_updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'æ›´æ–°æ—¶é—´',
  PRIMARY KEY (`share_id`),
  UNIQUE KEY `share_token` (`share_token`),
  KEY `share_file_id` (`share_file_id`),
  KEY `share_node_id` (`share_node_id`),
  KEY `idx_share_owner` (`share_owner_id`,`share_status`),
  KEY `idx_share_token` (`share_token`),
  KEY `idx_share_status` (`share_status`),
  CONSTRAINT `pcd_share_link_table_ibfk_1` FOREIGN KEY (`share_owner_id`) REFERENCES `pcd_user_info_table` (`user_id`) ON DELETE CASCADE,
  CONSTRAINT `pcd_share_link_table_ibfk_2` FOREIGN KEY (`share_file_id`) REFERENCES `pcd_file_info_table` (`file_id`) ON DELETE CASCADE,
  CONSTRAINT `pcd_share_link_table_ibfk_3` FOREIGN KEY (`share_node_id`) REFERENCES `pcd_directory_tree_table` (`node_id`) ON DELETE CASCADE,
  CONSTRAINT `chk_share_target` CHECK ((((`share_target_type` = _utf8mb4'file') and (`share_file_id` is not null) and (`share_node_id` is null)) or ((`share_target_type` = _utf8mb4'folder') and (`share_node_id` is not null) and (`share_file_id` is null))))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='åˆ†äº«é“¾æ¥ç®¡ç†è¡¨';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `pcd_share_link_table`
--

LOCK TABLES `pcd_share_link_table` WRITE;
/*!40000 ALTER TABLE `pcd_share_link_table` DISABLE KEYS */;
/*!40000 ALTER TABLE `pcd_share_link_table` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `pcd_space_join_request_table`
--

DROP TABLE IF EXISTS `pcd_space_join_request_table`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `pcd_space_join_request_table` (
  `request_id` bigint NOT NULL AUTO_INCREMENT COMMENT 'ç”³è¯·è®°å½•ID',
  `space_id` binary(16) NOT NULL COMMENT 'ç©ºé—´ID',
  `user_id` binary(16) NOT NULL COMMENT 'ç”³è¯·äººID',
  `request_message` text COMMENT 'ç”³è¯·ç•™è¨€',
  `status` enum('pending','approved','rejected') NOT NULL DEFAULT 'pending' COMMENT 'ç”³è¯·çŠ¶æ€',
  `reviewed_by` binary(16) DEFAULT NULL COMMENT 'å®¡æ‰¹äººID',
  `reviewed_at` datetime DEFAULT NULL COMMENT 'å®¡æ‰¹æ—¶é—´',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'ç”³è¯·æ—¶é—´',
  PRIMARY KEY (`request_id`),
  UNIQUE KEY `uk_space_user_pending` (`space_id`,`user_id`),
  KEY `reviewed_by` (`reviewed_by`),
  KEY `idx_space_pending` (`space_id`,`status`),
  KEY `idx_user_requests` (`user_id`,`status`),
  CONSTRAINT `pcd_space_join_request_table_ibfk_1` FOREIGN KEY (`space_id`) REFERENCES `pcd_space_table` (`space_id`) ON DELETE CASCADE,
  CONSTRAINT `pcd_space_join_request_table_ibfk_2` FOREIGN KEY (`user_id`) REFERENCES `pcd_user_info_table` (`user_id`) ON DELETE CASCADE,
  CONSTRAINT `pcd_space_join_request_table_ibfk_3` FOREIGN KEY (`reviewed_by`) REFERENCES `pcd_user_info_table` (`user_id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='ç©ºé—´åŠ å…¥ç”³è¯·è¡¨';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `pcd_space_join_request_table`
--

LOCK TABLES `pcd_space_join_request_table` WRITE;
/*!40000 ALTER TABLE `pcd_space_join_request_table` DISABLE KEYS */;
/*!40000 ALTER TABLE `pcd_space_join_request_table` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `pcd_space_member_table`
--

DROP TABLE IF EXISTS `pcd_space_member_table`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `pcd_space_member_table` (
  `member_id` bigint NOT NULL AUTO_INCREMENT COMMENT 'æˆå‘˜è®°å½•ID',
  `space_id` binary(16) NOT NULL COMMENT 'ç©ºé—´ID',
  `user_id` binary(16) NOT NULL COMMENT 'ç”¨æˆ·ID',
  `role` enum('owner','admin','editor','viewer') NOT NULL DEFAULT 'viewer' COMMENT 'æˆå‘˜è§’è‰²',
  `joined_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'åŠ å…¥æ—¶é—´',
  `invited_by` binary(16) DEFAULT NULL COMMENT 'é‚€è¯·äººID',
  PRIMARY KEY (`member_id`),
  UNIQUE KEY `uk_space_user` (`space_id`,`user_id`),
  KEY `invited_by` (`invited_by`),
  KEY `idx_user_spaces` (`user_id`,`space_id`),
  KEY `idx_space_role` (`space_id`,`role`),
  CONSTRAINT `pcd_space_member_table_ibfk_1` FOREIGN KEY (`space_id`) REFERENCES `pcd_space_table` (`space_id`) ON DELETE CASCADE,
  CONSTRAINT `pcd_space_member_table_ibfk_2` FOREIGN KEY (`user_id`) REFERENCES `pcd_user_info_table` (`user_id`) ON DELETE CASCADE,
  CONSTRAINT `pcd_space_member_table_ibfk_3` FOREIGN KEY (`invited_by`) REFERENCES `pcd_user_info_table` (`user_id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='ç©ºé—´æˆå‘˜è¡¨';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `pcd_space_member_table`
--

LOCK TABLES `pcd_space_member_table` WRITE;
/*!40000 ALTER TABLE `pcd_space_member_table` DISABLE KEYS */;
/*!40000 ALTER TABLE `pcd_space_member_table` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `pcd_space_permission_table`
--

DROP TABLE IF EXISTS `pcd_space_permission_table`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `pcd_space_permission_table` (
  `permission_id` bigint NOT NULL AUTO_INCREMENT COMMENT 'æƒé™è®°å½•ID',
  `space_id` binary(16) NOT NULL COMMENT 'ç©ºé—´ID',
  `user_id` binary(16) NOT NULL COMMENT 'ç”¨æˆ·ID',
  `target_node_id` binary(16) DEFAULT NULL COMMENT 'ç›®æ ‡èŠ‚ç‚¹IDï¼ˆNULL=ç©ºé—´çº§æƒé™ï¼ŒéNULL=ç›®å½•çº§æƒé™ï¼‰',
  `can_read` tinyint(1) NOT NULL DEFAULT '1' COMMENT 'è¯»æƒé™',
  `can_write` tinyint(1) NOT NULL DEFAULT '0' COMMENT 'å†™æƒé™ï¼ˆåˆ›å»º/ä¿®æ”¹æ–‡ä»¶ï¼‰',
  `can_delete` tinyint(1) NOT NULL DEFAULT '0' COMMENT 'åˆ é™¤æƒé™',
  `can_share` tinyint(1) NOT NULL DEFAULT '0' COMMENT 'åˆ†äº«æƒé™',
  `can_invite` tinyint(1) NOT NULL DEFAULT '0' COMMENT 'é‚€è¯·æˆå‘˜æƒé™',
  `can_manage` tinyint(1) NOT NULL DEFAULT '0' COMMENT 'ç®¡ç†æƒé™ï¼ˆä¿®æ”¹ç©ºé—´è®¾ç½®ï¼‰',
  `granted_by` binary(16) DEFAULT NULL COMMENT 'æˆæƒäººID',
  `granted_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'æˆæƒæ—¶é—´',
  PRIMARY KEY (`permission_id`),
  UNIQUE KEY `uk_space_user_node` (`space_id`,`user_id`,`target_node_id`),
  KEY `user_id` (`user_id`),
  KEY `target_node_id` (`target_node_id`),
  KEY `granted_by` (`granted_by`),
  KEY `idx_space_user` (`space_id`,`user_id`),
  CONSTRAINT `pcd_space_permission_table_ibfk_1` FOREIGN KEY (`space_id`) REFERENCES `pcd_space_table` (`space_id`) ON DELETE CASCADE,
  CONSTRAINT `pcd_space_permission_table_ibfk_2` FOREIGN KEY (`user_id`) REFERENCES `pcd_user_info_table` (`user_id`) ON DELETE CASCADE,
  CONSTRAINT `pcd_space_permission_table_ibfk_3` FOREIGN KEY (`target_node_id`) REFERENCES `pcd_directory_tree_table` (`node_id`) ON DELETE CASCADE,
  CONSTRAINT `pcd_space_permission_table_ibfk_4` FOREIGN KEY (`granted_by`) REFERENCES `pcd_user_info_table` (`user_id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='ç©ºé—´ç»†ç²’åº¦æƒé™è¡¨';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `pcd_space_permission_table`
--

LOCK TABLES `pcd_space_permission_table` WRITE;
/*!40000 ALTER TABLE `pcd_space_permission_table` DISABLE KEYS */;
/*!40000 ALTER TABLE `pcd_space_permission_table` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `pcd_space_table`
--

DROP TABLE IF EXISTS `pcd_space_table`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `pcd_space_table` (
  `space_id` binary(16) NOT NULL COMMENT 'ç©ºé—´å”¯ä¸€ID',
  `space_name` varchar(200) NOT NULL COMMENT 'ç©ºé—´åç§°',
  `space_type` enum('personal','enterprise','public','team') NOT NULL COMMENT 'ç©ºé—´ç±»å‹',
  `space_owner_id` binary(16) NOT NULL COMMENT 'ç©ºé—´åˆ›å»ºè€…/æ‰€æœ‰è€…',
  `space_quota` bigint NOT NULL DEFAULT '10737418240' COMMENT 'ç©ºé—´é…é¢ï¼ˆå­—èŠ‚ï¼‰ï¼Œé»˜è®¤10GB',
  `space_used` bigint NOT NULL DEFAULT '0' COMMENT 'å·²ç”¨å®¹é‡ï¼ˆå­—èŠ‚ï¼‰',
  `space_file_count` int NOT NULL DEFAULT '0' COMMENT 'æ–‡ä»¶æ•°é‡',
  `space_visibility` enum('private','public','whitelist','blacklist') NOT NULL DEFAULT 'private' COMMENT 'å¯è§æ€§æ§åˆ¶',
  `space_description` text COMMENT 'ç©ºé—´æè¿°',
  `space_avatar_path` varchar(512) DEFAULT NULL COMMENT 'ç©ºé—´å¤´åƒè·¯å¾„',
  `space_im_group_id` varchar(100) DEFAULT NULL COMMENT 'å…³è”IMç¾¤ç»„IDï¼ˆä¼ä¸š/å›¢é˜Ÿç©ºé—´è‡ªåŠ¨åˆ›å»ºï¼‰',
  `space_created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'åˆ›å»ºæ—¶é—´',
  `space_updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'æ›´æ–°æ—¶é—´',
  `space_status` enum('active','disabled','deleted') NOT NULL DEFAULT 'active' COMMENT 'ç©ºé—´çŠ¶æ€',
  PRIMARY KEY (`space_id`),
  UNIQUE KEY `uk_space_name_owner` (`space_name`,`space_owner_id`),
  KEY `idx_space_type` (`space_type`,`space_status`),
  KEY `idx_space_owner` (`space_owner_id`,`space_status`),
  KEY `idx_space_visibility` (`space_visibility`,`space_status`),
  CONSTRAINT `pcd_space_table_ibfk_1` FOREIGN KEY (`space_owner_id`) REFERENCES `pcd_user_info_table` (`user_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='ç©ºé—´ä¸»è¡¨';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `pcd_space_table`
--

LOCK TABLES `pcd_space_table` WRITE;
/*!40000 ALTER TABLE `pcd_space_table` DISABLE KEYS */;
/*!40000 ALTER TABLE `pcd_space_table` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `pcd_space_visibility_table`
--

DROP TABLE IF EXISTS `pcd_space_visibility_table`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `pcd_space_visibility_table` (
  `visibility_id` bigint NOT NULL AUTO_INCREMENT COMMENT 'å¯è§æ€§è®°å½•ID',
  `space_id` binary(16) NOT NULL COMMENT 'ç©ºé—´ID',
  `user_id` binary(16) NOT NULL COMMENT 'ç”¨æˆ·ID',
  `list_type` enum('whitelist','blacklist') NOT NULL COMMENT 'åå•ç±»å‹',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'æ·»åŠ æ—¶é—´',
  PRIMARY KEY (`visibility_id`),
  UNIQUE KEY `uk_space_user_list` (`space_id`,`user_id`,`list_type`),
  KEY `user_id` (`user_id`),
  KEY `idx_space_whitelist` (`space_id`,`list_type`),
  CONSTRAINT `pcd_space_visibility_table_ibfk_1` FOREIGN KEY (`space_id`) REFERENCES `pcd_space_table` (`space_id`) ON DELETE CASCADE,
  CONSTRAINT `pcd_space_visibility_table_ibfk_2` FOREIGN KEY (`user_id`) REFERENCES `pcd_user_info_table` (`user_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='ç©ºé—´å¯è§æ€§ç™½åå•/é»‘åå•è¡¨';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `pcd_space_visibility_table`
--

LOCK TABLES `pcd_space_visibility_table` WRITE;
/*!40000 ALTER TABLE `pcd_space_visibility_table` DISABLE KEYS */;
/*!40000 ALTER TABLE `pcd_space_visibility_table` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `pcd_tag_table`
--

DROP TABLE IF EXISTS `pcd_tag_table`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `pcd_tag_table` (
  `tag_id` bigint NOT NULL AUTO_INCREMENT COMMENT 'æ ‡ç­¾ID',
  `tag_user_id` binary(16) NOT NULL COMMENT 'æ‰€å±ç”¨æˆ·ID',
  `tag_name` varchar(50) NOT NULL COMMENT 'æ ‡ç­¾åç§°',
  `tag_color` varchar(7) NOT NULL DEFAULT '#3B82F6' COMMENT 'æ ‡ç­¾é¢œè‰²ï¼ˆHEXï¼‰',
  `tag_created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'åˆ›å»ºæ—¶é—´',
  PRIMARY KEY (`tag_id`),
  UNIQUE KEY `uk_user_tag` (`tag_user_id`,`tag_name`),
  KEY `idx_tag_user` (`tag_user_id`),
  CONSTRAINT `pcd_tag_table_ibfk_1` FOREIGN KEY (`tag_user_id`) REFERENCES `pcd_user_info_table` (`user_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='ç”¨æˆ·æ ‡ç­¾è¡¨';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `pcd_tag_table`
--

LOCK TABLES `pcd_tag_table` WRITE;
/*!40000 ALTER TABLE `pcd_tag_table` DISABLE KEYS */;
/*!40000 ALTER TABLE `pcd_tag_table` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `pcd_trash_target_table`
--

DROP TABLE IF EXISTS `pcd_trash_target_table`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `pcd_trash_target_table` (
  `trash_id` bigint NOT NULL AUTO_INCREMENT,
  `trash_target_id` binary(16) NOT NULL COMMENT 'åŸæ–‡ä»¶ID',
  `trash_target_type` enum('file','folder') NOT NULL COMMENT 'ç›®æ ‡ç±»å‹',
  `trash_user_id` binary(16) NOT NULL COMMENT 'ç”¨æˆ·ID',
  `trash_target_name` varchar(150) NOT NULL COMMENT 'æ–‡ä»¶åç§°',
  `trash_file_type` varchar(120) DEFAULT NULL COMMENT 'æ–‡ä»¶ç±»å‹',
  `trash_target_size` bigint DEFAULT NULL COMMENT 'æ–‡ä»¶å¤§å°',
  `trash_original_node_id` binary(16) NOT NULL COMMENT 'åŸèŠ‚ç‚¹ID',
  `trash_deleted_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'åˆ é™¤æ—¶é—´',
  `trash_expires_at` datetime NOT NULL COMMENT 'è¿‡æœŸæ—¶é—´',
  PRIMARY KEY (`trash_id`),
  KEY `idx_user_deleted` (`trash_user_id`,`trash_deleted_at`),
  KEY `idx_expires` (`trash_expires_at`),
  KEY `pcd_trash_target_table_trash_target_id_trash_target_type_index` (`trash_target_id`,`trash_target_type`),
  CONSTRAINT `pcd_trash_target_table_ibfk_1` FOREIGN KEY (`trash_user_id`) REFERENCES `pcd_user_info_table` (`user_id`) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=150 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='å›æ”¶ç«™æ–‡ä»¶è¡¨';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `pcd_trash_target_table`
--

LOCK TABLES `pcd_trash_target_table` WRITE;
/*!40000 ALTER TABLE `pcd_trash_target_table` DISABLE KEYS */;
/*!40000 ALTER TABLE `pcd_trash_target_table` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `pcd_upload_chunks_table`
--

DROP TABLE IF EXISTS `pcd_upload_chunks_table`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `pcd_upload_chunks_table` (
  `chunk_uploads_id` binary(16) NOT NULL COMMENT 'å…³è”ä¸Šä¼ ä¼šè¯ID',
  `chunk_index` int NOT NULL COMMENT 'åˆ‡ç‰‡ç´¢å¼•',
  `chunk_status` enum('pending','uploading','uploaded','failed') DEFAULT 'pending' COMMENT 'åˆ‡ç‰‡çŠ¶æ€',
  `chunk_storage_path` varchar(512) NOT NULL COMMENT 'åˆ‡ç‰‡å­˜å‚¨è·¯å¾„',
  `chunk_uploaded_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'åˆ‡ç‰‡ä¸Šä¼ æ—¶é—´',
  PRIMARY KEY (`chunk_uploads_id`,`chunk_index`),
  CONSTRAINT `pcd_upload_chunks_table_ibfk_1` FOREIGN KEY (`chunk_uploads_id`) REFERENCES `pcd_uploads_session_table` (`uploads_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='æ–‡ä»¶åˆ‡ç‰‡è¡¨';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `pcd_upload_chunks_table`
--

LOCK TABLES `pcd_upload_chunks_table` WRITE;
/*!40000 ALTER TABLE `pcd_upload_chunks_table` DISABLE KEYS */;
INSERT INTO `pcd_upload_chunks_table` VALUES (_binary '\r\Èz\ï²0GË¿Ç€\ë\Ë<Z',1,'uploaded','../Uploads/0dc87aef-b230-47cb-bfc7-8014ebcb3c5a-1.part','2026-06-13 18:39:10'),(_binary '/^\'IŒAMŠ!Ú¦.­‹$',1,'uploaded','../Uploads/2f5e2749-8c41-4d8a-9e21-daa62ead8b24-1.part','2026-06-22 21:08:06'),(_binary 'hšÒ”fúDš—\Õq\İd´',1,'uploaded','../Uploads/689ad294-66fa-449a-97d5-71dd026410b4-1.part','2026-06-23 05:09:07'),(_binary '¿O„xI£¤K­Ÿ\Û\î',1,'uploaded','../Uploads/bf4f018f-8478-49a3-a407-4bad049fdbee-1.part','2026-06-14 08:58:55'),(_binary '¿O„xI£¤K­Ÿ\Û\î',2,'uploaded','../Uploads/bf4f018f-8478-49a3-a407-4bad049fdbee-2.part','2026-06-14 08:58:55'),(_binary '¿O„xI£¤K­Ÿ\Û\î',3,'uploaded','../Uploads/bf4f018f-8478-49a3-a407-4bad049fdbee-3.part','2026-06-14 08:58:55'),(_binary '¿O„xI£¤K­Ÿ\Û\î',4,'uploaded','../Uploads/bf4f018f-8478-49a3-a407-4bad049fdbee-4.part','2026-06-14 08:58:56'),(_binary '¿O„xI£¤K­Ÿ\Û\î',5,'uploaded','../Uploads/bf4f018f-8478-49a3-a407-4bad049fdbee-5.part','2026-06-14 08:58:56'),(_binary '¿O„xI£¤K­Ÿ\Û\î',6,'uploaded','../Uploads/bf4f018f-8478-49a3-a407-4bad049fdbee-6.part','2026-06-14 08:58:56'),(_binary '¿O„xI£¤K­Ÿ\Û\î',7,'uploaded','../Uploads/bf4f018f-8478-49a3-a407-4bad049fdbee-7.part','2026-06-14 08:58:56'),(_binary '¿O„xI£¤K­Ÿ\Û\î',8,'uploaded','../Uploads/bf4f018f-8478-49a3-a407-4bad049fdbee-8.part','2026-06-14 08:58:56'),(_binary '¿O„xI£¤K­Ÿ\Û\î',9,'uploaded','../Uploads/bf4f018f-8478-49a3-a407-4bad049fdbee-9.part','2026-06-14 08:58:56'),(_binary '¿O„xI£¤K­Ÿ\Û\î',10,'uploaded','../Uploads/bf4f018f-8478-49a3-a407-4bad049fdbee-10.part','2026-06-14 08:58:56'),(_binary '¿O„xI£¤K­Ÿ\Û\î',11,'uploaded','../Uploads/bf4f018f-8478-49a3-a407-4bad049fdbee-11.part','2026-06-14 08:58:56'),(_binary '¿O„xI£¤K­Ÿ\Û\î',12,'uploaded','../Uploads/bf4f018f-8478-49a3-a407-4bad049fdbee-12.part','2026-06-14 08:58:56'),(_binary '¿O„xI£¤K­Ÿ\Û\î',13,'uploaded','../Uploads/bf4f018f-8478-49a3-a407-4bad049fdbee-13.part','2026-06-14 08:58:57'),(_binary '¿O„xI£¤K­Ÿ\Û\î',14,'uploaded','../Uploads/bf4f018f-8478-49a3-a407-4bad049fdbee-14.part','2026-06-14 08:58:56'),(_binary '¿O„xI£¤K­Ÿ\Û\î',15,'uploaded','../Uploads/bf4f018f-8478-49a3-a407-4bad049fdbee-15.part','2026-06-14 08:58:57'),(_binary '¿O„xI£¤K­Ÿ\Û\î',16,'uploaded','../Uploads/bf4f018f-8478-49a3-a407-4bad049fdbee-16.part','2026-06-14 08:58:57'),(_binary '¿O„xI£¤K­Ÿ\Û\î',17,'uploaded','../Uploads/bf4f018f-8478-49a3-a407-4bad049fdbee-17.part','2026-06-14 08:58:57'),(_binary '¿O„xI£¤K­Ÿ\Û\î',18,'uploaded','../Uploads/bf4f018f-8478-49a3-a407-4bad049fdbee-18.part','2026-06-14 08:58:57'),(_binary '¿O„xI£¤K­Ÿ\Û\î',19,'uploaded','../Uploads/bf4f018f-8478-49a3-a407-4bad049fdbee-19.part','2026-06-14 08:58:57'),(_binary '¿O„xI£¤K­Ÿ\Û\î',20,'uploaded','../Uploads/bf4f018f-8478-49a3-a407-4bad049fdbee-20.part','2026-06-14 08:58:57'),(_binary '¿O„xI£¤K­Ÿ\Û\î',21,'uploaded','../Uploads/bf4f018f-8478-49a3-a407-4bad049fdbee-21.part','2026-06-14 08:58:57'),(_binary '¿O„xI£¤K­Ÿ\Û\î',22,'uploaded','../Uploads/bf4f018f-8478-49a3-a407-4bad049fdbee-22.part','2026-06-14 08:58:57'),(_binary '¿O„xI£¤K­Ÿ\Û\î',23,'uploaded','../Uploads/bf4f018f-8478-49a3-a407-4bad049fdbee-23.part','2026-06-14 08:58:57'),(_binary '¿O„xI£¤K­Ÿ\Û\î',24,'uploaded','../Uploads/bf4f018f-8478-49a3-a407-4bad049fdbee-24.part','2026-06-14 08:58:57'),(_binary '¿O„xI£¤K­Ÿ\Û\î',25,'uploaded','../Uploads/bf4f018f-8478-49a3-a407-4bad049fdbee-25.part','2026-06-14 08:58:58'),(_binary '¿O„xI£¤K­Ÿ\Û\î',26,'uploaded','../Uploads/bf4f018f-8478-49a3-a407-4bad049fdbee-26.part','2026-06-14 08:58:58'),(_binary '¿O„xI£¤K­Ÿ\Û\î',27,'uploaded','../Uploads/bf4f018f-8478-49a3-a407-4bad049fdbee-27.part','2026-06-14 08:58:58'),(_binary '¿O„xI£¤K­Ÿ\Û\î',28,'uploaded','../Uploads/bf4f018f-8478-49a3-a407-4bad049fdbee-28.part','2026-06-14 08:58:58'),(_binary '¿O„xI£¤K­Ÿ\Û\î',29,'uploaded','../Uploads/bf4f018f-8478-49a3-a407-4bad049fdbee-29.part','2026-06-14 08:58:58'),(_binary '¿O„xI£¤K­Ÿ\Û\î',30,'uploaded','../Uploads/bf4f018f-8478-49a3-a407-4bad049fdbee-30.part','2026-06-14 08:58:58'),(_binary '¿O„xI£¤K­Ÿ\Û\î',31,'uploaded','../Uploads/bf4f018f-8478-49a3-a407-4bad049fdbee-31.part','2026-06-14 08:58:58'),(_binary '¿O„xI£¤K­Ÿ\Û\î',32,'uploaded','../Uploads/bf4f018f-8478-49a3-a407-4bad049fdbee-32.part','2026-06-14 08:58:58'),(_binary '¿O„xI£¤K­Ÿ\Û\î',33,'uploaded','../Uploads/bf4f018f-8478-49a3-a407-4bad049fdbee-33.part','2026-06-14 08:58:58'),(_binary '¿O„xI£¤K­Ÿ\Û\î',34,'uploaded','../Uploads/bf4f018f-8478-49a3-a407-4bad049fdbee-34.part','2026-06-14 08:58:58'),(_binary '¿O„xI£¤K­Ÿ\Û\î',35,'uploaded','../Uploads/bf4f018f-8478-49a3-a407-4bad049fdbee-35.part','2026-06-14 08:58:58'),(_binary '¿O„xI£¤K­Ÿ\Û\î',36,'uploaded','../Uploads/bf4f018f-8478-49a3-a407-4bad049fdbee-36.part','2026-06-14 08:58:58'),(_binary '¿O„xI£¤K­Ÿ\Û\î',37,'uploaded','../Uploads/bf4f018f-8478-49a3-a407-4bad049fdbee-37.part','2026-06-14 08:58:59'),(_binary '¿O„xI£¤K­Ÿ\Û\î',38,'uploaded','../Uploads/bf4f018f-8478-49a3-a407-4bad049fdbee-38.part','2026-06-14 08:58:59'),(_binary '¿O„xI£¤K­Ÿ\Û\î',39,'uploaded','../Uploads/bf4f018f-8478-49a3-a407-4bad049fdbee-39.part','2026-06-14 08:58:59'),(_binary '¿O„xI£¤K­Ÿ\Û\î',40,'uploaded','../Uploads/bf4f018f-8478-49a3-a407-4bad049fdbee-40.part','2026-06-14 08:58:59'),(_binary '¿O„xI£¤K­Ÿ\Û\î',41,'uploaded','../Uploads/bf4f018f-8478-49a3-a407-4bad049fdbee-41.part','2026-06-14 08:58:59'),(_binary '¿O„xI£¤K­Ÿ\Û\î',42,'uploaded','../Uploads/bf4f018f-8478-49a3-a407-4bad049fdbee-42.part','2026-06-14 08:58:59'),(_binary '¿O„xI£¤K­Ÿ\Û\î',43,'uploaded','../Uploads/bf4f018f-8478-49a3-a407-4bad049fdbee-43.part','2026-06-14 08:59:00'),(_binary '¿O„xI£¤K­Ÿ\Û\î',44,'uploaded','../Uploads/bf4f018f-8478-49a3-a407-4bad049fdbee-44.part','2026-06-14 08:59:00'),(_binary '¿O„xI£¤K­Ÿ\Û\î',45,'uploaded','../Uploads/bf4f018f-8478-49a3-a407-4bad049fdbee-45.part','2026-06-14 08:59:00'),(_binary '¿O„xI£¤K­Ÿ\Û\î',46,'uploaded','../Uploads/bf4f018f-8478-49a3-a407-4bad049fdbee-46.part','2026-06-14 08:59:00'),(_binary '¿O„xI£¤K­Ÿ\Û\î',47,'uploaded','../Uploads/bf4f018f-8478-49a3-a407-4bad049fdbee-47.part','2026-06-14 08:59:00'),(_binary '¿O„xI£¤K­Ÿ\Û\î',48,'uploaded','../Uploads/bf4f018f-8478-49a3-a407-4bad049fdbee-48.part','2026-06-14 08:59:00'),(_binary '¿O„xI£¤K­Ÿ\Û\î',49,'uploaded','../Uploads/bf4f018f-8478-49a3-a407-4bad049fdbee-49.part','2026-06-14 08:59:00'),(_binary '¿O„xI£¤K­Ÿ\Û\î',50,'uploaded','../Uploads/bf4f018f-8478-49a3-a407-4bad049fdbee-50.part','2026-06-14 08:59:00'),(_binary '¿O„xI£¤K­Ÿ\Û\î',51,'uploaded','../Uploads/bf4f018f-8478-49a3-a407-4bad049fdbee-51.part','2026-06-14 08:59:00'),(_binary '¿O„xI£¤K­Ÿ\Û\î',52,'uploaded','../Uploads/bf4f018f-8478-49a3-a407-4bad049fdbee-52.part','2026-06-14 08:59:01'),(_binary '¿O„xI£¤K­Ÿ\Û\î',53,'uploaded','../Uploads/bf4f018f-8478-49a3-a407-4bad049fdbee-53.part','2026-06-14 08:59:01'),(_binary '¿O„xI£¤K­Ÿ\Û\î',54,'uploaded','../Uploads/bf4f018f-8478-49a3-a407-4bad049fdbee-54.part','2026-06-14 08:59:01'),(_binary '¿O„xI£¤K­Ÿ\Û\î',55,'uploaded','../Uploads/bf4f018f-8478-49a3-a407-4bad049fdbee-55.part','2026-06-14 08:59:01'),(_binary '¿O„xI£¤K­Ÿ\Û\î',56,'uploaded','../Uploads/bf4f018f-8478-49a3-a407-4bad049fdbee-56.part','2026-06-14 08:59:01'),(_binary '¿O„xI£¤K­Ÿ\Û\î',57,'uploaded','../Uploads/bf4f018f-8478-49a3-a407-4bad049fdbee-57.part','2026-06-14 08:59:01'),(_binary '¿O„xI£¤K­Ÿ\Û\î',58,'uploaded','../Uploads/bf4f018f-8478-49a3-a407-4bad049fdbee-58.part','2026-06-14 08:59:01'),(_binary '¿O„xI£¤K­Ÿ\Û\î',59,'uploaded','../Uploads/bf4f018f-8478-49a3-a407-4bad049fdbee-59.part','2026-06-14 08:59:01'),(_binary '¿O„xI£¤K­Ÿ\Û\î',60,'uploaded','../Uploads/bf4f018f-8478-49a3-a407-4bad049fdbee-60.part','2026-06-14 08:59:02'),(_binary '¿O„xI£¤K­Ÿ\Û\î',61,'uploaded','../Uploads/bf4f018f-8478-49a3-a407-4bad049fdbee-61.part','2026-06-14 08:59:02'),(_binary '¿O„xI£¤K­Ÿ\Û\î',62,'uploaded','../Uploads/bf4f018f-8478-49a3-a407-4bad049fdbee-62.part','2026-06-14 08:59:02'),(_binary '¿O„xI£¤K­Ÿ\Û\î',63,'uploaded','../Uploads/bf4f018f-8478-49a3-a407-4bad049fdbee-63.part','2026-06-14 08:59:02'),(_binary '¿O„xI£¤K­Ÿ\Û\î',64,'uploaded','../Uploads/bf4f018f-8478-49a3-a407-4bad049fdbee-64.part','2026-06-14 08:59:02'),(_binary '¿O„xI£¤K­Ÿ\Û\î',65,'uploaded','../Uploads/bf4f018f-8478-49a3-a407-4bad049fdbee-65.part','2026-06-14 08:59:02'),(_binary '¿O„xI£¤K­Ÿ\Û\î',66,'uploaded','../Uploads/bf4f018f-8478-49a3-a407-4bad049fdbee-66.part','2026-06-14 08:59:02'),(_binary '¿O„xI£¤K­Ÿ\Û\î',67,'uploaded','../Uploads/bf4f018f-8478-49a3-a407-4bad049fdbee-67.part','2026-06-14 08:59:02'),(_binary '¿O„xI£¤K­Ÿ\Û\î',68,'uploaded','../Uploads/bf4f018f-8478-49a3-a407-4bad049fdbee-68.part','2026-06-14 08:59:02'),(_binary '¿O„xI£¤K­Ÿ\Û\î',69,'uploaded','../Uploads/bf4f018f-8478-49a3-a407-4bad049fdbee-69.part','2026-06-14 08:59:02'),(_binary '¿O„xI£¤K­Ÿ\Û\î',70,'uploaded','../Uploads/bf4f018f-8478-49a3-a407-4bad049fdbee-70.part','2026-06-14 08:59:03'),(_binary '¿O„xI£¤K­Ÿ\Û\î',71,'uploaded','../Uploads/bf4f018f-8478-49a3-a407-4bad049fdbee-71.part','2026-06-14 08:59:03'),(_binary '¿O„xI£¤K­Ÿ\Û\î',72,'uploaded','../Uploads/bf4f018f-8478-49a3-a407-4bad049fdbee-72.part','2026-06-14 08:59:03'),(_binary '¿O„xI£¤K­Ÿ\Û\î',73,'uploaded','../Uploads/bf4f018f-8478-49a3-a407-4bad049fdbee-73.part','2026-06-14 08:59:04'),(_binary '¿O„xI£¤K­Ÿ\Û\î',74,'uploaded','../Uploads/bf4f018f-8478-49a3-a407-4bad049fdbee-74.part','2026-06-14 08:59:04'),(_binary '\Ùÿ§\Èw®E¬–L`/«¼',1,'uploaded','../Uploads/d9ffa7c8-77ae-45ac-9602-4c607f2fabbc-1.part','2026-06-14 18:22:32');
/*!40000 ALTER TABLE `pcd_upload_chunks_table` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `pcd_uploads_session_table`
--

DROP TABLE IF EXISTS `pcd_uploads_session_table`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `pcd_uploads_session_table` (
  `uploads_id` binary(16) NOT NULL,
  `uploads_user_id` binary(16) NOT NULL COMMENT 'ä¸Šä¼ ç”¨æˆ·ID',
  `uploads_total_chunks` int NOT NULL COMMENT 'ä¸Šä¼ åˆ‡ç‰‡æ€»æ•°',
  `uploads_starting_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'ä¸Šä¼ å¼€å§‹æ—¶é—´',
  `uploads_endding_time` timestamp NOT NULL COMMENT 'ä¸Šä¼ ç»“æŸæ—¶é—´',
  `uploads_file_size` bigint NOT NULL COMMENT 'æ–‡ä»¶å¤§å°',
  `uploads_file_checksum` varchar(256) NOT NULL COMMENT 'æ–‡ä»¶æ ¡éªŒå€¼',
  `uploads_chunks_max_size` int NOT NULL COMMENT 'åˆ‡ç‰‡æœ€å¤§å¤§å°',
  `uploads_file_name` varchar(150) NOT NULL COMMENT 'æ–‡ä»¶åç§°',
  `uploads_file_type` varchar(120) NOT NULL COMMENT 'æ–‡ä»¶ç±»å‹',
  `uploads_node_id` binary(16) NOT NULL COMMENT 'æ–‡ä»¶æ‰€åœ¨ç›®å½•èŠ‚ç‚¹ID',
  `uploads_status` enum('uploading','merging','completed','failed','canceled','deleted') DEFAULT 'uploading' COMMENT 'ä¸Šä¼ çŠ¶æ€',
  `uploads_space_id` binary(16) DEFAULT NULL COMMENT 'æ‰€å±ç©ºé—´ID',
  PRIMARY KEY (`uploads_id`),
  KEY `uploads_user_id` (`uploads_user_id`),
  KEY `fk_uploads_session_directory_tree` (`uploads_node_id`),
  KEY `idx_uploads_space` (`uploads_space_id`,`uploads_status`),
  CONSTRAINT `fk_uploads_session_directory_tree` FOREIGN KEY (`uploads_node_id`) REFERENCES `pcd_directory_tree_table` (`node_id`) ON DELETE CASCADE,
  CONSTRAINT `pcd_uploads_session_table_ibfk_1` FOREIGN KEY (`uploads_user_id`) REFERENCES `pcd_user_info_table` (`user_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='æ–‡ä»¶ä¸Šä¼ ä¼šè¯è¡¨';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `pcd_uploads_session_table`
--

LOCK TABLES `pcd_uploads_session_table` WRITE;
/*!40000 ALTER TABLE `pcd_uploads_session_table` DISABLE KEYS */;
INSERT INTO `pcd_uploads_session_table` VALUES (_binary '\r\Èz\ï²0GË¿Ç€\ë\Ë<Z',_binary 'UUUUUUUUUUUUUUUU',1,'2026-06-13 18:39:08','2026-06-13 19:09:08',39962,'9b35266211d0ab20f5ac865472f43d57bf782fe0f90f7cb28de9ad7e78e89171',5242880,'index-2.html','text/html',_binary 'ªªªªªªªªªªªªªªª¥','deleted',NULL),(_binary '\ç9o:lGŠŒµY…\ô\òR',_binary 'UUUUUUUUUUUUUUUU',1,'2026-07-05 11:07:11','2026-07-05 11:37:11',478844,'07261043b59ecdbf31023b264f932a5257968e047355228cd7985350346a04e2',5242880,'ç¬¬01124223432141æ¬¡å®è®­è¯¾ ç®€å†åˆ¶ä½œ æœ€ç»ˆæ•ˆæœ.docx','application/vnd.openxmlformats-officedocument.wordprocessingml.document',_binary '×’Aİ»\ñ@‰ic±&2N','deleted',NULL),(_binary '•\Ç?YLÇ‰&\ÛG‰ÿ}',_binary 'UUUUUUUUUUUUUUUU',1,'2026-07-05 11:04:15','2026-07-05 11:34:15',478844,'07261043b59ecdbf31023b264f932a5257968e047355228cd7985350346a04e2',5242880,'ç¬¬1æ¬¡å®è®­è¯¾ ç®€å†åˆ¶ä½œ æœ€ç»ˆæ•ˆæœ.docx','application/vnd.openxmlformats-officedocument.wordprocessingml.document',_binary '×’Aİ»\ñ@‰ic±&2N','deleted',NULL),(_binary '$©\Ü\ÛBƒ\õ/\Ì\íD›',_binary 'UUUUUUUUUUUUUUUU',1,'2026-07-05 11:04:31','2026-07-05 11:34:31',478844,'07261043b59ecdbf31023b264f932a5257968e047355228cd7985350346a04e2',5242880,'ç¬¬6æ¬¡å®è®­è¯¾ ç®€å†åˆ¶ä½œ æœ€ç»ˆæ•ˆæœ.docx','application/vnd.openxmlformats-officedocument.wordprocessingml.document',_binary '×’Aİ»\ñ@‰ic±&2N','deleted',NULL),(_binary '/^\'IŒAMŠ!Ú¦.­‹$',_binary 'UUUUUUUUUUUUUUUU',1,'2026-06-22 21:08:05','2026-06-22 21:38:05',88,'374c5408d89f41e5258d51b37fe2b5070dc49ed00f0af1d4ec8a554f9a684ddf',5242880,'dump.rdb','application/octet-stream',_binary 'ªªªªªªªªªªªªªªª¥','deleted',NULL),(_binary '7NS\Ü\Æ@¤+¿Ó¬\r¸',_binary 'UUUUUUUUUUUUUUUU',1,'2026-07-05 11:07:18','2026-07-05 11:37:18',478844,'07261043b59ecdbf31023b264f932a5257968e047355228cd7985350346a04e2',5242880,'ç¬¬01124223432141æ¬¡å®è®­è¯¾ ç®€å†åˆ¶ä½œ æœ€ç»ˆæ•ˆæœ.docx','application/vnd.openxmlformats-officedocument.wordprocessingml.document',_binary '×’Aİ»\ñ@‰ic±&2N','deleted',NULL),(_binary '<\â´\åD©c+—\ô\"',_binary 'UUUUUUUUUUUUUUUU',1,'2026-07-05 11:04:25','2026-07-05 11:34:25',478844,'07261043b59ecdbf31023b264f932a5257968e047355228cd7985350346a04e2',5242880,'ç¬¬4æ¬¡å®è®­è¯¾ ç®€å†åˆ¶ä½œ æœ€ç»ˆæ•ˆæœ.docx','application/vnd.openxmlformats-officedocument.wordprocessingml.document',_binary '×’Aİ»\ñ@‰ic±&2N','deleted',NULL),(_binary 'M¥Qá¥šOgŸ/±\òM2b\Í',_binary 'UUUUUUUUUUUUUUUU',1,'2026-07-05 11:07:19','2026-07-05 11:37:19',478844,'07261043b59ecdbf31023b264f932a5257968e047355228cd7985350346a04e2',5242880,'ç¬¬01124223432141æ¬¡å®è®­è¯¾ ç®€å†åˆ¶ä½œ æœ€ç»ˆæ•ˆæœ.docx','application/vnd.openxmlformats-officedocument.wordprocessingml.document',_binary '×’Aİ»\ñ@‰ic±&2N','deleted',NULL),(_binary '`zˆ“¤G°\îÂ¥­;\Ã\Î',_binary 'UUUUUUUUUUUUUUUU',1,'2026-07-05 11:04:21','2026-07-05 11:34:21',478844,'07261043b59ecdbf31023b264f932a5257968e047355228cd7985350346a04e2',5242880,'ç¬¬3æ¬¡å®è®­è¯¾ ç®€å†åˆ¶ä½œ æœ€ç»ˆæ•ˆæœ.docx','application/vnd.openxmlformats-officedocument.wordprocessingml.document',_binary '×’Aİ»\ñ@‰ic±&2N','deleted',NULL),(_binary 'hšÒ”fúDš—\Õq\İd´',_binary 'UUUUUUUUUUUUUUUU',1,'2026-06-23 05:09:06','2026-06-23 05:39:06',2843,'20dff912b16e7f771f77d00055707a3f0fbe9d3b34450a73e9b6adea37dec7df',5242880,'jbr_err_pid49370.log','application/octet-stream',_binary 'ªªªªªªªªªªªªªªª¥','deleted',NULL),(_binary 'ud¬D£‹F\"‹\÷<Z­´¥',_binary 'UUUUUUUUUUUUUUUU',1,'2026-07-05 11:07:17','2026-07-05 11:37:17',478844,'07261043b59ecdbf31023b264f932a5257968e047355228cd7985350346a04e2',5242880,'ç¬¬01124223432141æ¬¡å®è®­è¯¾ ç®€å†åˆ¶ä½œ æœ€ç»ˆæ•ˆæœ.docx','application/vnd.openxmlformats-officedocument.wordprocessingml.document',_binary '×’Aİ»\ñ@‰ic±&2N','deleted',NULL),(_binary 'Œ\ŞF©MT¯VŒ‚i†J',_binary 'UUUUUUUUUUUUUUUU',1,'2026-07-05 11:04:28','2026-07-05 11:34:28',478844,'07261043b59ecdbf31023b264f932a5257968e047355228cd7985350346a04e2',5242880,'ç¬¬5æ¬¡å®è®­è¯¾ ç®€å†åˆ¶ä½œ æœ€ç»ˆæ•ˆæœ.docx','application/vnd.openxmlformats-officedocument.wordprocessingml.document',_binary '×’Aİ»\ñ@‰ic±&2N','deleted',NULL),(_binary '“«{zCÔ©Ÿ\ËbD“',_binary 'UUUUUUUUUUUUUUUU',1,'2026-07-05 11:07:21','2026-07-05 11:37:21',478844,'07261043b59ecdbf31023b264f932a5257968e047355228cd7985350346a04e2',5242880,'ç¬¬01124223432141æ¬¡å®è®­è¯¾ ç®€å†åˆ¶ä½œ æœ€ç»ˆæ•ˆæœ.docx','application/vnd.openxmlformats-officedocument.wordprocessingml.document',_binary '×’Aİ»\ñ@‰ic±&2N','deleted',NULL),(_binary '±œ‘Vş}Kd¬8Á\ÚI…vd',_binary 'UUUUUUUUUUUUUUUU',1,'2026-07-05 11:04:36','2026-07-05 11:34:36',478844,'07261043b59ecdbf31023b264f932a5257968e047355228cd7985350346a04e2',5242880,'ç¬¬8æ¬¡å®è®­è¯¾ ç®€å†åˆ¶ä½œ æœ€ç»ˆæ•ˆæœ.docx','application/vnd.openxmlformats-officedocument.wordprocessingml.document',_binary '×’Aİ»\ñ@‰ic±&2N','deleted',NULL),(_binary '¹‘a—’\×D\éº:±¿Ê 1',_binary 'UUUUUUUUUUUUUUUU',1,'2026-07-05 11:04:40','2026-07-05 11:34:40',478844,'07261043b59ecdbf31023b264f932a5257968e047355228cd7985350346a04e2',5242880,'ç¬¬0æ¬¡å®è®­è¯¾ ç®€å†åˆ¶ä½œ æœ€ç»ˆæ•ˆæœ.docx','application/vnd.openxmlformats-officedocument.wordprocessingml.document',_binary '×’Aİ»\ñ@‰ic±&2N','deleted',NULL),(_binary '¿O„xI£¤K­Ÿ\Û\î',_binary 'UUUUUUUUUUUUUUUU',74,'2026-06-14 08:58:54','2026-06-14 09:28:54',383744933,'6b47537f8e733eb2bacd880a43c378216bf9753e699dae150de12ce27d9eea0f',5242880,'java_error_in_studio.hprof','application/octet-stream',_binary 'ªªªªªªªªªªªªªªª¥','deleted',NULL),(_binary '\Ù:\ò“ıF\ì©MKT‹ùü',_binary 'UUUUUUUUUUUUUUUU',1,'2026-06-22 21:04:54','2026-06-22 21:34:54',2843,'20dff912b16e7f771f77d00055707a3f0fbe9d3b34450a73e9b6adea37dec7df',5242880,'jbr_err_pid49370.log','application/octet-stream',_binary 'ªªªªªªªªªªªªªªª¥','deleted',NULL),(_binary '\Ùÿ§\Èw®E¬–L`/«¼',_binary 'UUUUUUUUUUUUUUUU',1,'2026-06-14 18:22:31','2026-06-14 18:52:31',1536,'8b0f6bd56051930131e764b70e501cbcaeb6c36bc1afc975c6ec386169113009',5242880,'connect.py','text/x-python-script',_binary 'ªªªªªªªªªªªªªªª¥','deleted',NULL);
/*!40000 ALTER TABLE `pcd_uploads_session_table` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `pcd_user_device_table`
--

DROP TABLE IF EXISTS `pcd_user_device_table`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `pcd_user_device_table` (
  `device_id` binary(16) NOT NULL COMMENT 'æœåŠ¡ç«¯ç”Ÿæˆçš„è®¾å¤‡ID',
  `device_user_id` binary(16) NOT NULL COMMENT 'æ‰€å±ç”¨æˆ·ID',
  `device_client_type` varchar(50) NOT NULL COMMENT 'å®¢æˆ·ç«¯ç±»å‹',
  `device_client_name` varchar(120) DEFAULT NULL COMMENT 'å®¢æˆ·ç«¯å±•ç¤ºåç§°',
  `device_platform` varchar(120) DEFAULT NULL COMMENT 'ç³»ç»Ÿæˆ–å¹³å°ä¿¡æ¯',
  `device_user_agent_hash` varchar(64) DEFAULT NULL COMMENT 'User-Agentå“ˆå¸Œ',
  `device_public_key` text COMMENT 'è®¾å¤‡å…¬é’¥',
  `device_created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `device_last_seen_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `device_status` enum('active','disabled','revoked') NOT NULL DEFAULT 'active',
  PRIMARY KEY (`device_id`),
  KEY `idx_device_user_status` (`device_user_id`,`device_status`),
  CONSTRAINT `pcd_user_device_table_ibfk_1` FOREIGN KEY (`device_user_id`) REFERENCES `pcd_user_info_table` (`user_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='ç”¨æˆ·ç™»å½•è®¾å¤‡è¡¨';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `pcd_user_device_table`
--

LOCK TABLES `pcd_user_device_table` WRITE;
/*!40000 ALTER TABLE `pcd_user_device_table` DISABLE KEYS */;
/*!40000 ALTER TABLE `pcd_user_device_table` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `pcd_user_info_table`
--

DROP TABLE IF EXISTS `pcd_user_info_table`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `pcd_user_info_table` (
  `user_name` varchar(120) NOT NULL COMMENT 'ç”¨æˆ·å',
  `user_id` binary(16) NOT NULL,
  `user_phone_number` varchar(50) DEFAULT NULL,
  `user_image_path` varchar(512) DEFAULT NULL COMMENT 'ç”¨æˆ·å¤´åƒè·¯å¾„',
  `user_password` varchar(70) NOT NULL COMMENT 'ç”¨æˆ·å¯†ç ',
  `user_account` varchar(70) NOT NULL COMMENT 'ç”¨æˆ·è´¦å·',
  `user_email` varchar(70) DEFAULT NULL COMMENT 'ç”¨æˆ·é‚®ç®±',
  PRIMARY KEY (`user_id`),
  UNIQUE KEY `user_account` (`user_account`),
  UNIQUE KEY `user_phone_number` (`user_phone_number`),
  UNIQUE KEY `user_email` (`user_email`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='ç”¨æˆ·ä¿¡æ¯è¡¨';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `pcd_user_info_table`
--

LOCK TABLES `pcd_user_info_table` WRITE;
/*!40000 ALTER TABLE `pcd_user_info_table` DISABLE KEYS */;
INSERT INTO `pcd_user_info_table` VALUES ('æµ‹è¯•ç”¨æˆ·A',_binary '','18800000001',NULL,'$2b$12$hTdCVHfX0zas0oO2F1Z.muLx7gHnRZSeoKDktsMgSHugizk/RmtOS','test_user_a','test_user_a@pcd.local'),('æµ‹è¯•ç”¨æˆ·B',_binary '\"\"\"\"\"\"\"\"\"\"\"\"\"\"\"\"','18800000002',NULL,'$2b$12$hTdCVHfX0zas0oO2F1Z.muLx7gHnRZSeoKDktsMgSHugizk/RmtOS','test_user_b','test_user_b@pcd.local'),('æµ‹è¯•ç”¨æˆ·C',_binary '3333333333333333','18800000003',NULL,'$2b$12$hTdCVHfX0zas0oO2F1Z.muLx7gHnRZSeoKDktsMgSHugizk/RmtOS','test_user_c','test_user_c@pcd.local'),('Mwz',_binary '7\àmZhŸC•»@\ál\Ö','18800000006',NULL,'$2a$12$ZlyDRRXdCGRxbGc1mw5OQ.x/gc2wMSYdgkNWs4aUz5j74ZHVRAicq','pcd_12537010823','hellomwz@outlook.com'),('XiaoMo',_binary 'A]0d¤eHB\Öñª›‡À','15777446691',NULL,'$2b$12$hTdCVHfX0zas0oO2F1Z.muLx7gHnRZSeoKDktsMgSHugizk/RmtOS','pcd_18181999067','1773172144@qq.com'),('æµ‹è¯•ç”¨æˆ·D',_binary 'DDDDDDDDDDDDDDDD','18800000004',NULL,'$2b$12$hTdCVHfX0zas0oO2F1Z.muLx7gHnRZSeoKDktsMgSHugizk/RmtOS','test_user_d','test_user_d@pcd.local'),('æµ‹è¯•ç”¨æˆ·E',_binary 'UUUUUUUUUUUUUUUU','18800000005',NULL,'$2b$12$hTdCVHfX0zas0oO2F1Z.muLx7gHnRZSeoKDktsMgSHugizk/RmtOS','test_user_e','test_user_e@pcd.local');
/*!40000 ALTER TABLE `pcd_user_info_table` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `pcd_user_quota_log_table`
--

DROP TABLE IF EXISTS `pcd_user_quota_log_table`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `pcd_user_quota_log_table` (
  `quota_log_id` bigint NOT NULL AUTO_INCREMENT,
  `quota_log_user_id` binary(16) NOT NULL COMMENT 'ç”¨æˆ·ID',
  `quota_log_change_type` varchar(20) NOT NULL COMMENT 'å˜æ›´ç±»å‹',
  `quota_log_change_bytes` bigint NOT NULL COMMENT 'å˜æ›´å­—èŠ‚æ•°',
  `quota_log_before_total` bigint DEFAULT NULL COMMENT 'å˜æ›´å‰æ€»é¢åº¦',
  `quota_log_after_total` bigint DEFAULT NULL COMMENT 'å˜æ›´åæ€»é¢åº¦',
  `quota_log_before_used` bigint DEFAULT NULL COMMENT 'å˜æ›´å‰å·²ç”¨',
  `quota_log_after_used` bigint DEFAULT NULL COMMENT 'å˜æ›´åå·²ç”¨',
  `quota_log_operator` varchar(50) DEFAULT 'SYSTEM' COMMENT 'æ“ä½œäºº',
  `quota_log_created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`quota_log_id`),
  KEY `idx_user_id_time` (`quota_log_user_id`,`quota_log_created_at`),
  CONSTRAINT `pcd_user_quota_log_table_ibfk_1` FOREIGN KEY (`quota_log_user_id`) REFERENCES `pcd_user_info_table` (`user_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='é…é¢å˜æ›´æ—¥å¿—';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `pcd_user_quota_log_table`
--

LOCK TABLES `pcd_user_quota_log_table` WRITE;
/*!40000 ALTER TABLE `pcd_user_quota_log_table` DISABLE KEYS */;
/*!40000 ALTER TABLE `pcd_user_quota_log_table` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `pcd_user_quota_table`
--

DROP TABLE IF EXISTS `pcd_user_quota_table`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `pcd_user_quota_table` (
  `quota_id` bigint NOT NULL AUTO_INCREMENT,
  `quota_user_id` binary(16) NOT NULL COMMENT 'ç”¨æˆ·ID',
  `quota_total_capacity` bigint NOT NULL DEFAULT '10737418240' COMMENT 'æ€»é¢åº¦',
  `quota_used_capacity` bigint NOT NULL DEFAULT '0' COMMENT 'å·²ç”¨å®¹é‡',
  `quota_file_count` int NOT NULL DEFAULT '0' COMMENT 'å·²ä¸Šä¼ æ–‡ä»¶æ•°é‡',
  `quota_version` int NOT NULL DEFAULT '0' COMMENT 'ä¹è§‚é”ç‰ˆæœ¬å·',
  `quota_created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `quota_updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `quota_released_capacity` bigint DEFAULT '0',
  PRIMARY KEY (`quota_id`),
  UNIQUE KEY `quota_user_id` (`quota_user_id`),
  KEY `idx_user_id` (`quota_user_id`),
  CONSTRAINT `pcd_user_quota_table_ibfk_1` FOREIGN KEY (`quota_user_id`) REFERENCES `pcd_user_info_table` (`user_id`) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=8 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='ç”¨æˆ·å­˜å‚¨é…é¢è¡¨';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `pcd_user_quota_table`
--

LOCK TABLES `pcd_user_quota_table` WRITE;
/*!40000 ALTER TABLE `pcd_user_quota_table` DISABLE KEYS */;
INSERT INTO `pcd_user_quota_table` VALUES (1,_binary '',10737418240,0,0,0,'2026-06-23 04:57:26','2026-06-23 04:57:26',0),(2,_binary '\"\"\"\"\"\"\"\"\"\"\"\"\"\"\"\"',10737418240,0,0,0,'2026-06-23 04:57:26','2026-06-23 04:57:26',0),(3,_binary '3333333333333333',10737418240,0,0,0,'2026-06-23 04:57:26','2026-06-23 04:57:26',0),(4,_binary '7\àmZhŸC•»@\ál\Ö',10737418240,0,0,0,'2026-06-23 04:57:26','2026-06-23 04:57:26',0),(5,_binary 'A]0d¤eHB\Öñª›‡À',10737418240,0,0,0,'2026-06-23 04:57:26','2026-06-23 04:57:26',0),(6,_binary 'DDDDDDDDDDDDDDDD',10737418240,0,0,0,'2026-06-23 04:57:26','2026-06-23 04:57:26',0),(7,_binary 'UUUUUUUUUUUUUUUU',10737418240,374051964,259,550,'2026-06-23 04:57:26','2026-07-13 23:37:23',410);
/*!40000 ALTER TABLE `pcd_user_quota_table` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `registration_challenges`
--

DROP TABLE IF EXISTS `registration_challenges`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `registration_challenges` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `challenge` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'æŒ‘æˆ˜å€¼ï¼ˆUUID v4ï¼‰',
  `client_public_key` text COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'å®¢æˆ·ç«¯å…¬é’¥ï¼ˆä¸´æ—¶ï¼‰',
  `platform` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'macOS' COMMENT 'å¹³å°',
  `expires_at` datetime NOT NULL COMMENT 'è¿‡æœŸæ—¶é—´',
  `used` tinyint(1) NOT NULL DEFAULT '0' COMMENT 'æ˜¯å¦å·²ä½¿ç”¨',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `idx_challenge` (`challenge`),
  KEY `idx_expires_at` (`expires_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='æ³¨å†ŒæŒ‘æˆ˜å€¼ç¼“å­˜è¡¨';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `registration_challenges`
--

LOCK TABLES `registration_challenges` WRITE;
/*!40000 ALTER TABLE `registration_challenges` DISABLE KEYS */;
/*!40000 ALTER TABLE `registration_challenges` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-07-19  3:41:50
