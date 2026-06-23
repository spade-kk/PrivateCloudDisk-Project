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
INSERT INTO `pcd_directory_closure_table` VALUES (_binary 'UUUUUUUUUUUUUUUU',_binary '4 tG[A\è½b4£—v',_binary '4 tG[A\è½b4£—v',0),(_binary 'UUUUUUUUUUUUUUUU',_binary 'qpK¡wO¡¹ee$\ï',_binary 'qpK¡wO¡¹ee$\ï',0),(_binary 'UUUUUUUUUUUUUUUU',_binary '<W\êşH\ì¬\Ö\Ü\Ç\ã×…\ß',_binary '<W\êşH\ì¬\Ö\Ü\Ç\ã×…\ß',0),(_binary '',_binary 'ªªªªªªªªªªªªªªª¡',_binary 'ªªªªªªªªªªªªªªª¡',0),(_binary '\"\"\"\"\"\"\"\"\"\"\"\"\"\"\"\"',_binary 'ªªªªªªªªªªªªªªª¢',_binary 'ªªªªªªªªªªªªªªª¢',0),(_binary '3333333333333333',_binary 'ªªªªªªªªªªªªªªª£',_binary 'ªªªªªªªªªªªªªªª£',0),(_binary 'DDDDDDDDDDDDDDDD',_binary 'ªªªªªªªªªªªªªªª¤',_binary 'ªªªªªªªªªªªªªªª¤',0),(_binary 'UUUUUUUUUUUUUUUU',_binary 'ªªªªªªªªªªªªªªª¥',_binary '4 tG[A\è½b4£—v',2),(_binary 'UUUUUUUUUUUUUUUU',_binary 'ªªªªªªªªªªªªªªª¥',_binary 'qpK¡wO¡¹ee$\ï',1),(_binary 'UUUUUUUUUUUUUUUU',_binary 'ªªªªªªªªªªªªªªª¥',_binary '<W\êşH\ì¬\Ö\Ü\Ç\ã×…\ß',1),(_binary 'UUUUUUUUUUUUUUUU',_binary 'ªªªªªªªªªªªªªªª¥',_binary 'ªªªªªªªªªªªªªªª¥',0),(_binary 'UUUUUUUUUUUUUUUU',_binary 'ªªªªªªªªªªªªªªª¥',_binary '\ËBÙ¢e\ÒI\'‹¦\\\Èv‚',1),(_binary 'UUUUUUUUUUUUUUUU',_binary 'ªªªªªªªªªªªªªªª¥',_binary '\ÕgRFË#V\È\ØB\Í(',1),(_binary '7\àmZhŸC•»@\ál\Ö',_binary '®­zUZ\İCÚœ½\r\ÄWˆ',_binary '®­zUZ\İCÚœ½\r\ÄWˆ',0),(_binary 'UUUUUUUUUUUUUUUU',_binary '\ËBÙ¢e\ÒI\'‹¦\\\Èv‚',_binary '4 tG[A\è½b4£—v',1),(_binary 'UUUUUUUUUUUUUUUU',_binary '\ËBÙ¢e\ÒI\'‹¦\\\Èv‚',_binary '\ËBÙ¢e\ÒI\'‹¦\\\Èv‚',0),(_binary 'UUUUUUUUUUUUUUUU',_binary '\ÕgRFË#V\È\ØB\Í(',_binary '\ÕgRFË#V\È\ØB\Í(',0);
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
  PRIMARY KEY (`node_id`),
  UNIQUE KEY `uk_directory_tree` (`node_id`,`node_user_id`,`node_parent_id`),
  KEY `node_user_id` (`node_user_id`),
  KEY `node_parent_id` (`node_parent_id`),
  CONSTRAINT `pcd_directory_tree_table_ibfk_1` FOREIGN KEY (`node_user_id`) REFERENCES `pcd_user_info_table` (`user_id`) ON DELETE CASCADE,
  CONSTRAINT `pcd_directory_tree_table_ibfk_2` FOREIGN KEY (`node_parent_id`) REFERENCES `pcd_directory_tree_table` (`node_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='èŠ‚ç‚¹ç›®å½•æ ‘è¡¨';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `pcd_directory_tree_table`
--

LOCK TABLES `pcd_directory_tree_table` WRITE;
/*!40000 ALTER TABLE `pcd_directory_tree_table` DISABLE KEYS */;
INSERT INTO `pcd_directory_tree_table` VALUES (_binary '4 tG[A\è½b4£—v',_binary 'UUUUUUUUUUUUUUUU',_binary '\ËBÙ¢e\ÒI\'‹¦\\\Èv‚','tests','2026-06-14 09:57:10','active'),(_binary 'qpK¡wO¡¹ee$\ï',_binary 'UUUUUUUUUUUUUUUU',_binary 'ªªªªªªªªªªªªªªª¥','9','2026-06-13 17:09:14','deleted'),(_binary '<W\êşH\ì¬\Ö\Ü\Ç\ã×…\ß',_binary 'UUUUUUUUUUUUUUUU',_binary 'ªªªªªªªªªªªªªªª¥','data','2026-06-14 09:57:27','deleted'),(_binary 'ªªªªªªªªªªªªªªª¡',_binary '',NULL,'root','2026-06-10 14:52:38','active'),(_binary 'ªªªªªªªªªªªªªªª¢',_binary '\"\"\"\"\"\"\"\"\"\"\"\"\"\"\"\"',NULL,'root','2026-06-10 14:52:38','active'),(_binary 'ªªªªªªªªªªªªªªª£',_binary '3333333333333333',NULL,'root','2026-06-10 14:52:38','active'),(_binary 'ªªªªªªªªªªªªªªª¤',_binary 'DDDDDDDDDDDDDDDD',NULL,'root','2026-06-10 14:52:38','active'),(_binary 'ªªªªªªªªªªªªªªª¥',_binary 'UUUUUUUUUUUUUUUU',NULL,'root','2026-06-10 14:52:38','active'),(_binary '®­zUZ\İCÚœ½\r\ÄWˆ',_binary '7\àmZhŸC•»@\ál\Ö',NULL,'#root','2026-06-21 15:30:30','active'),(_binary '\ËBÙ¢e\ÒI\'‹¦\\\Èv‚',_binary 'UUUUUUUUUUUUUUUU',_binary 'ªªªªªªªªªªªªªªª¥','hello','2026-06-14 09:57:15','active'),(_binary '\ÕgRFË#V\È\ØB\Í(',_binary 'UUUUUUUUUUUUUUUU',_binary 'ªªªªªªªªªªªªªªª¥','111111111','2026-06-14 09:57:19','active');
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
  PRIMARY KEY (`file_id`),
  UNIQUE KEY `uk_file_info` (`file_id`,`file_author_id`,`file_node_id`),
  KEY `file_author_id` (`file_author_id`),
  KEY `fk_file_info_directory_tree` (`file_node_id`),
  CONSTRAINT `fk_file_info_directory_tree` FOREIGN KEY (`file_node_id`) REFERENCES `pcd_directory_tree_table` (`node_id`) ON DELETE CASCADE,
  CONSTRAINT `pcd_file_info_table_ibfk_1` FOREIGN KEY (`file_author_id`) REFERENCES `pcd_user_info_table` (`user_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='æ–‡ä»¶ä¿¡æ¯è¡¨';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `pcd_file_info_table`
--

LOCK TABLES `pcd_file_info_table` WRITE;
/*!40000 ALTER TABLE `pcd_file_info_table` DISABLE KEYS */;
INSERT INTO `pcd_file_info_table` VALUES ('EXCELæ•™ç¨‹ | æ‹œæ‰˜ä¸‰è¿äº†ï¼å…¨Bç«™æœ€ç”¨å¿ƒï¼ˆæ²¡æœ‰ä¹‹ä¸€ï¼‰çš„EXCELå…è´¹è¯¾ç¨‹ï¼OFFICE-WPS-è¡¨æ ¼-EXCELå‡½æ•°-EXCELæŠ€å·§-æ•°æ®åˆ†æ-åŠå…¬è½¯ä»¶ - 004 - S03-å¿«é€Ÿå¡«å……ï¼Œæ‰¹é‡æå–ä¸ç»„åˆæ•°æ®çš„ç¥å¥‡.mp4','2026-06-14 18:44:17',33758169,'video/mp4',_binary 'UUUUUUUUUUUUUUUU',_binary '\âü$\ÜA¥v\ò#G\î','e459ac11a97001e117b60c5b65e7a8e491976a17282ab7680f909f1a3105fae0',7,_binary 'ªªªªªªªªªªªªªªª¥','../Uploads/storage/64f6c940-4b3b-4cc9-aabf-af9225e0de93-7.cloud','active'),('connect.py','2026-06-14 18:22:32',1536,'text/x-python-script',_binary 'UUUUUUUUUUUUUUUU',_binary '\"2[\'©F§’â‹¤€u\ã','8b0f6bd56051930131e764b70e501cbcaeb6c36bc1afc975c6ec386169113009',1,_binary 'ªªªªªªªªªªªªªªª¥',NULL,'merging'),('java_error_in_studio.hprof','2026-06-14 08:59:04',383744933,'application/octet-stream',_binary 'UUUUUUUUUUUUUUUU',_binary 'P–\Í\Ö\ã*@\0¾®t\r\ä\Üy','6b47537f8e733eb2bacd880a43c378216bf9753e699dae150de12ce27d9eea0f',74,_binary 'ªªªªªªªªªªªªªªª¥',NULL,'merging'),('JavaäºŒçº§å¤§çº².pdf','2026-06-13 19:38:18',120547,'application/pdf',_binary 'UUUUUUUUUUUUUUUU',_binary 'r	/ }K‹§’­;©','3ea82b01db7948e60f471ae6ac23beb6ea659b69077a35a91689c7dd306606d3',1,_binary 'ªªªªªªªªªªªªªªª¥','../Uploads/storage/0b71e23c-e14b-4ee8-93d7-c218fbb73be1-1.cloud','deleted'),('tests7.py','2026-06-13 19:05:22',4638,'text/x-python-script',_binary 'UUUUUUUUUUUUUUUU',_binary 't‡=Ï\ÌIÇ¯¾;c\Õg','aeab8ac72fbe88e484ab1a921352fb0cf6b074ee57b5b5826fe3254daf1e9c13',1,_binary 'ªªªªªªªªªªªªªªª¥','../Uploads/storage/da9f4f0d-4837-4075-886c-247253dca1c6-1.cloud','deleted'),('client-download-2.html','2026-06-14 18:44:44',48100,'text/html',_binary 'UUUUUUUUUUUUUUUU',_binary 'yµ\õ@K.DIš)\åm|\Z','3e92f6fdf5c8da98173517c439650673dbfec19a7254904422e1a96c6f7a8005',1,_binary 'ªªªªªªªªªªªªªªª¥','../Uploads/storage/80fb0d51-b7bc-4305-bab9-649d88e282ae-1.cloud','active'),('ã€Šå®æ—¶ç¢°æ’æ£€æµ‹ã€‹.pptx','2026-06-14 18:43:30',42317380,'application/vnd.openxmlformats-officedocument.presentationml.presentation',_binary 'UUUUUUUUUUUUUUUU',_binary '‰G0×¥\ÅOE‰º\Ü\Â0\ñ¾\Ş','70e11359955d3d830c7a42d90455a75870593ed90f2d045147f09d092825171d',9,_binary 'ªªªªªªªªªªªªªªª¥','../Uploads/storage/15891069-f205-423f-9bd9-a316527a481d-9.cloud','active'),('gclient','2026-06-23 05:22:29',322,'application/octet-stream',_binary 'UUUUUUUUUUUUUUUU',_binary '\Ê\0±”\ÖAº\Øü\ö™\ÓW³','c26028908bafe0cdfc578aa3d54a45dcea71adb9f28e081783ca557138cc1998',1,_binary 'ªªªªªªªªªªªªªªª¥','../Uploads/storage/fbc47d90-98ab-40c3-8746-05b30b8a0c5a-1.cloud','active'),('REST API basics- CRUD, test & variable.postman_collection.json','2026-06-13 19:37:59',21243,'application/json',_binary 'UUUUUUUUUUUUUUUU',_binary '°o\÷¦f_D‡„\r*/¤%\'p','6265c07867f05e56e4b44e95de27ceb376b7b918c2a0dd31fe21ce78e567286d',1,_binary 'ªªªªªªªªªªªªªªª¥','../Uploads/storage/8628811f-8e55-4a04-a861-bc769ae860a9-1.cloud','deleted'),('ç§æœ‰äº‘ç›˜ç³»ç»Ÿ - å®Œæ•´APIæµ‹è¯•-documentation.html','2026-06-14 05:21:00',39838,'text/html',_binary 'UUUUUUUUUUUUUUUU',_binary '³b\åFIÚ¹<¡\Ø@!','f4cfaace78a88f6911833f1f41cf20b1b46e56006d2003d8dc2797420049db3d',1,_binary 'ªªªªªªªªªªªªªªª¥','../Uploads/storage/376947d9-3cb3-47a7-b76d-89f3f020f9a2-1.cloud','deleted'),('dump.rdb','2026-06-23 05:16:54',88,'application/octet-stream',_binary 'UUUUUUUUUUUUUUUU',_binary '\Ê\İ\õ¶\âKÔºuX\\(0','374c5408d89f41e5258d51b37fe2b5070dc49ed00f0af1d4ec8a554f9a684ddf',1,_binary 'ªªªªªªªªªªªªªªª¥','../Uploads/storage/46f765de-7766-4f2d-a627-6a7842dac811-1.cloud','active'),('DB368C5971D4DD40BDFDE00C39154A84.jpg','2026-06-14 18:42:48',820229,'image/jpeg',_binary 'UUUUUUUUUUUUUUUU',_binary '\çE\ò4S¸F’µ–)B\Û\éIk','f62761e04a2f678599c297f69a9a189b31fa1d27989e64b863ba10196b39ec3b',1,_binary 'ªªªªªªªªªªªªªªª¥','../Uploads/storage/ccee1b9c-9111-4583-bde5-7573597ff148-1.cloud','active');
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='æ–‡ä»¶/æ–‡ä»¶å¤¹æ”¶è—è¡¨';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `pcd_file_star_table`
--

LOCK TABLES `pcd_file_star_table` WRITE;
/*!40000 ALTER TABLE `pcd_file_star_table` DISABLE KEYS */;
/*!40000 ALTER TABLE `pcd_file_star_table` ENABLE KEYS */;
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='IM ä¼šè¯è¡¨';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `pcd_im_conversation`
--

LOCK TABLES `pcd_im_conversation` WRITE;
/*!40000 ALTER TABLE `pcd_im_conversation` DISABLE KEYS */;
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='IM ç¾¤ç»„è¡¨';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `pcd_im_group`
--

LOCK TABLES `pcd_im_group` WRITE;
/*!40000 ALTER TABLE `pcd_im_group` DISABLE KEYS */;
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='IM ç¾¤ç»„æˆå‘˜è¡¨';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `pcd_im_group_member`
--

LOCK TABLES `pcd_im_group_member` WRITE;
/*!40000 ALTER TABLE `pcd_im_group_member` DISABLE KEYS */;
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='IM æ¶ˆæ¯è¡¨';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `pcd_im_message`
--

LOCK TABLES `pcd_im_message` WRITE;
/*!40000 ALTER TABLE `pcd_im_message` DISABLE KEYS */;
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
) ENGINE=InnoDB AUTO_INCREMENT=116 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='å›æ”¶ç«™æ–‡ä»¶è¡¨';
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
  PRIMARY KEY (`uploads_id`),
  KEY `uploads_user_id` (`uploads_user_id`),
  KEY `fk_uploads_session_directory_tree` (`uploads_node_id`),
  CONSTRAINT `fk_uploads_session_directory_tree` FOREIGN KEY (`uploads_node_id`) REFERENCES `pcd_directory_tree_table` (`node_id`) ON DELETE CASCADE,
  CONSTRAINT `pcd_uploads_session_table_ibfk_1` FOREIGN KEY (`uploads_user_id`) REFERENCES `pcd_user_info_table` (`user_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='æ–‡ä»¶ä¸Šä¼ ä¼šè¯è¡¨';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `pcd_uploads_session_table`
--

LOCK TABLES `pcd_uploads_session_table` WRITE;
/*!40000 ALTER TABLE `pcd_uploads_session_table` DISABLE KEYS */;
INSERT INTO `pcd_uploads_session_table` VALUES (_binary '\r\Èz\ï²0GË¿Ç€\ë\Ë<Z',_binary 'UUUUUUUUUUUUUUUU',1,'2026-06-13 18:39:08','2026-06-13 19:09:08',39962,'9b35266211d0ab20f5ac865472f43d57bf782fe0f90f7cb28de9ad7e78e89171',5242880,'index-2.html','text/html',_binary 'ªªªªªªªªªªªªªªª¥','merging'),(_binary '/^\'IŒAMŠ!Ú¦.­‹$',_binary 'UUUUUUUUUUUUUUUU',1,'2026-06-22 21:08:05','2026-06-22 21:38:05',88,'374c5408d89f41e5258d51b37fe2b5070dc49ed00f0af1d4ec8a554f9a684ddf',5242880,'dump.rdb','application/octet-stream',_binary 'ªªªªªªªªªªªªªªª¥','uploading'),(_binary 'hšÒ”fúDš—\Õq\İd´',_binary 'UUUUUUUUUUUUUUUU',1,'2026-06-23 05:09:06','2026-06-23 05:39:06',2843,'20dff912b16e7f771f77d00055707a3f0fbe9d3b34450a73e9b6adea37dec7df',5242880,'jbr_err_pid49370.log','application/octet-stream',_binary 'ªªªªªªªªªªªªªªª¥','uploading'),(_binary '¿O„xI£¤K­Ÿ\Û\î',_binary 'UUUUUUUUUUUUUUUU',74,'2026-06-14 08:58:54','2026-06-14 09:28:54',383744933,'6b47537f8e733eb2bacd880a43c378216bf9753e699dae150de12ce27d9eea0f',5242880,'java_error_in_studio.hprof','application/octet-stream',_binary 'ªªªªªªªªªªªªªªª¥','merging'),(_binary '\Ù:\ò“ıF\ì©MKT‹ùü',_binary 'UUUUUUUUUUUUUUUU',1,'2026-06-22 21:04:54','2026-06-22 21:34:54',2843,'20dff912b16e7f771f77d00055707a3f0fbe9d3b34450a73e9b6adea37dec7df',5242880,'jbr_err_pid49370.log','application/octet-stream',_binary 'ªªªªªªªªªªªªªªª¥','uploading'),(_binary '\Ùÿ§\Èw®E¬–L`/«¼',_binary 'UUUUUUUUUUUUUUUU',1,'2026-06-14 18:22:31','2026-06-14 18:52:31',1536,'8b0f6bd56051930131e764b70e501cbcaeb6c36bc1afc975c6ec386169113009',5242880,'connect.py','text/x-python-script',_binary 'ªªªªªªªªªªªªªªª¥','merging');
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
INSERT INTO `pcd_user_quota_table` VALUES (1,_binary '',10737418240,0,0,0,'2026-06-23 04:57:26','2026-06-23 04:57:26',0),(2,_binary '\"\"\"\"\"\"\"\"\"\"\"\"\"\"\"\"',10737418240,0,0,0,'2026-06-23 04:57:26','2026-06-23 04:57:26',0),(3,_binary '3333333333333333',10737418240,0,0,0,'2026-06-23 04:57:26','2026-06-23 04:57:26',0),(4,_binary '7\àmZhŸC•»@\ál\Ö',10737418240,0,0,0,'2026-06-23 04:57:26','2026-06-23 04:57:26',0),(5,_binary 'A]0d¤eHB\Öñª›‡À',10737418240,0,0,0,'2026-06-23 04:57:26','2026-06-23 04:57:26',0),(6,_binary 'DDDDDDDDDDDDDDDD',10737418240,0,0,0,'2026-06-23 04:57:26','2026-06-23 04:57:26',0),(7,_binary 'UUUUUUUUUUUUUUUU',10737418240,0,0,5,'2026-06-23 04:57:26','2026-06-23 13:22:28',6184);
/*!40000 ALTER TABLE `pcd_user_quota_table` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-06-24  4:36:40
