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
-- Current Database: `private_cloud_disk`
--

CREATE DATABASE /*!32312 IF NOT EXISTS*/ `private_cloud_disk` /*!40100 DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci */ /*!80016 DEFAULT ENCRYPTION='N' */;

USE `private_cloud_disk`;

--
-- Table structure for table `pcd_directory_tree_table`
--

DROP TABLE IF EXISTS `pcd_directory_tree_table`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `pcd_directory_tree_table` (
  `node_id` varchar(36) NOT NULL,
  `node_user_id` varchar(36) NOT NULL,
  `node_parent_id` varchar(36) DEFAULT NULL,
  `node_name` varchar(200) NOT NULL,
  `node_create_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `node_status` enum('lock','active','pending') DEFAULT 'active',
  PRIMARY KEY (`node_id`),
  KEY `node_user_id` (`node_user_id`),
  KEY `node_parent_id` (`node_parent_id`),
  CONSTRAINT `pcd_directory_tree_table_ibfk_1` FOREIGN KEY (`node_user_id`) REFERENCES `pcd_user_info_table` (`user_id`) ON DELETE CASCADE,
  CONSTRAINT `pcd_directory_tree_table_ibfk_2` FOREIGN KEY (`node_parent_id`) REFERENCES `pcd_directory_tree_table` (`node_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `pcd_directory_tree_table`
--

LOCK TABLES `pcd_directory_tree_table` WRITE;
/*!40000 ALTER TABLE `pcd_directory_tree_table` DISABLE KEYS */;
INSERT INTO `pcd_directory_tree_table` VALUES ('086462ca-48f8-4533-bbe9-016981784c38','415d3064-a465-4813-8f42-d6f1aa9b87c0','d8785081-e2bf-4ced-97fa-3b785de3abc6','上山','2025-12-03 14:35:29','lock'),('2aa3b627-efe4-409a-a36a-00c8fd1a800b','415d3064-a465-4813-8f42-d6f1aa9b87c0','3ae9b5e5-96c0-40bd-95f7-23907363084b','66','2025-12-03 16:16:20','pending'),('2ecb5fa6-0133-4894-b6e7-b1c6928f25d2','415d3064-a465-4813-8f42-d6f1aa9b87c0','d8785081-e2bf-4ced-97fa-3b785de3abc6','是','2025-12-03 16:09:32','pending'),('316a48ee-d0c2-4167-8d5f-8824a4329dd1','415d3064-a465-4813-8f42-d6f1aa9b87c0','fe06b689-afa7-4bc2-865a-79a220226edf','不清楚','2025-12-03 16:17:18','pending'),('366e4da5-1a07-4c76-9de1-787d6a56d85f','415d3064-a465-4813-8f42-d6f1aa9b87c0','3ae9b5e5-96c0-40bd-95f7-23907363084b','55','2025-12-03 16:16:25','pending'),('3ae9b5e5-96c0-40bd-95f7-23907363084b','415d3064-a465-4813-8f42-d6f1aa9b87c0','d8785081-e2bf-4ced-97fa-3b785de3abc6','1','2025-12-03 07:08:18','lock'),('568cb2a2-7451-47f9-8981-665d1a13894a','415d3064-a465-4813-8f42-d6f1aa9b87c0','d8785081-e2bf-4ced-97fa-3b785de3abc6','2','2025-12-03 07:12:49','pending'),('6b827495-f87a-45ea-9e01-013efb9fc40a','415d3064-a465-4813-8f42-d6f1aa9b87c0','d8785081-e2bf-4ced-97fa-3b785de3abc6','小吃','2025-12-03 14:39:42','pending'),('81a957cb-7580-4ad0-83fd-c6d651b20fb3','415d3064-a465-4813-8f42-d6f1aa9b87c0','d8785081-e2bf-4ced-97fa-3b785de3abc6','333','2025-12-03 14:32:31','pending'),('b69ffe75-d8cd-431b-9195-eee41ac7b9c5','415d3064-a465-4813-8f42-d6f1aa9b87c0','d8785081-e2bf-4ced-97fa-3b785de3abc6','244','2025-12-03 07:12:53','pending'),('bf07088f-ae01-422a-a9a3-997474e7f483','415d3064-a465-4813-8f42-d6f1aa9b87c0','fe06b689-afa7-4bc2-865a-79a220226edf','22','2025-12-03 16:16:52','pending'),('d0c234c3-868b-4beb-9570-61ba51b141b6','415d3064-a465-4813-8f42-d6f1aa9b87c0','d8785081-e2bf-4ced-97fa-3b785de3abc6','sss','2025-12-03 14:32:38','pending'),('d8785081-e2bf-4ced-97fa-3b785de3abc6','415d3064-a465-4813-8f42-d6f1aa9b87c0',NULL,'#root','2025-08-02 15:40:15','lock'),('eb1c76bb-9687-4a03-a182-98d22963d867','415d3064-a465-4813-8f42-d6f1aa9b87c0','086462ca-48f8-4533-bbe9-016981784c38','11','2025-12-03 16:16:57','pending'),('f27f84ca-3d35-4d93-80ff-9b3582214dc9','415d3064-a465-4813-8f42-d6f1aa9b87c0','d8785081-e2bf-4ced-97fa-3b785de3abc6','最爱言言','2025-12-06 13:58:39','pending'),('fe06b689-afa7-4bc2-865a-79a220226edf','415d3064-a465-4813-8f42-d6f1aa9b87c0','d8785081-e2bf-4ced-97fa-3b785de3abc6','2445','2025-12-03 08:12:20','lock');
/*!40000 ALTER TABLE `pcd_directory_tree_table` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `pcd_download_chunks_table`
--

DROP TABLE IF EXISTS `pcd_download_chunks_table`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `pcd_download_chunks_table` (
  `chunk_download_id` varchar(36) NOT NULL,
  `chunk_index` int NOT NULL,
  `chunk_status` enum('pending','lock','active','invaild') DEFAULT 'pending',
  `chunk_storage_path` varchar(512) NOT NULL,
  `chunk_created_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `chunk_checksum` varchar(256) NOT NULL,
  PRIMARY KEY (`chunk_download_id`,`chunk_index`),
  CONSTRAINT `pcd_download_chunks_table_ibfk_1` FOREIGN KEY (`chunk_download_id`) REFERENCES `pcd_download_session_table` (`download_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `pcd_download_chunks_table`
--

LOCK TABLES `pcd_download_chunks_table` WRITE;
/*!40000 ALTER TABLE `pcd_download_chunks_table` DISABLE KEYS */;
/*!40000 ALTER TABLE `pcd_download_chunks_table` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `pcd_download_session_table`
--

DROP TABLE IF EXISTS `pcd_download_session_table`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `pcd_download_session_table` (
  `download_id` varchar(36) NOT NULL,
  `download_user_id` varchar(36) NOT NULL,
  `download_total_chunks` int NOT NULL,
  `download_starting_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `download_endding_time` timestamp NOT NULL,
  `download_file_name` varchar(150) NOT NULL,
  `download_file_type` varchar(60) NOT NULL,
  `download_file_parent_node_id` varchar(36) NOT NULL,
  `download_status` enum('downloading','completed','failed') DEFAULT 'downloading',
  PRIMARY KEY (`download_id`),
  KEY `download_user_id` (`download_user_id`),
  KEY `download_file_parent_node_id` (`download_file_parent_node_id`),
  CONSTRAINT `pcd_download_session_table_ibfk_1` FOREIGN KEY (`download_user_id`) REFERENCES `pcd_user_info_table` (`user_id`) ON DELETE CASCADE,
  CONSTRAINT `pcd_download_session_table_ibfk_2` FOREIGN KEY (`download_file_parent_node_id`) REFERENCES `pcd_directory_tree_table` (`node_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `pcd_download_session_table`
--

LOCK TABLES `pcd_download_session_table` WRITE;
/*!40000 ALTER TABLE `pcd_download_session_table` DISABLE KEYS */;
/*!40000 ALTER TABLE `pcd_download_session_table` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `pcd_file_chunks_table`
--

DROP TABLE IF EXISTS `pcd_file_chunks_table`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `pcd_file_chunks_table` (
  `chunk_file_id` varchar(36) NOT NULL,
  `chunk_index` int NOT NULL,
  `chunk_storage_path` varchar(512) NOT NULL,
  `chunk_created_time` timestamp NOT NULL,
  `chunk_checksum` varchar(256) NOT NULL,
  PRIMARY KEY (`chunk_file_id`,`chunk_index`),
  CONSTRAINT `pcd_file_chunks_table_ibfk_1` FOREIGN KEY (`chunk_file_id`) REFERENCES `pcd_file_info_table` (`file_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `pcd_file_chunks_table`
--

LOCK TABLES `pcd_file_chunks_table` WRITE;
/*!40000 ALTER TABLE `pcd_file_chunks_table` DISABLE KEYS */;
/*!40000 ALTER TABLE `pcd_file_chunks_table` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `pcd_file_info_table`
--

DROP TABLE IF EXISTS `pcd_file_info_table`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `pcd_file_info_table` (
  `file_name` varchar(150) NOT NULL,
  `file_uploaded_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `file_size` bigint NOT NULL,
  `file_type` varchar(60) NOT NULL,
  `file_author_id` varchar(36) NOT NULL,
  `file_id` varchar(36) NOT NULL,
  `file_checksum` varchar(256) NOT NULL,
  `file_node_id` varchar(36) NOT NULL,
  `file_total_chunks` int NOT NULL,
  PRIMARY KEY (`file_id`),
  KEY `fk_file_info_user_info` (`file_author_id`),
  KEY `fk_file_info_directory_tree` (`file_node_id`),
  CONSTRAINT `fk_file_info_directory_tree` FOREIGN KEY (`file_node_id`) REFERENCES `pcd_directory_tree_table` (`node_id`) ON DELETE CASCADE,
  CONSTRAINT `fk_file_info_user_info` FOREIGN KEY (`file_author_id`) REFERENCES `pcd_user_info_table` (`user_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `pcd_file_info_table`
--

LOCK TABLES `pcd_file_info_table` WRITE;
/*!40000 ALTER TABLE `pcd_file_info_table` DISABLE KEYS */;
INSERT INTO `pcd_file_info_table` VALUES ('V1.0原理图.pdf','2025-08-05 04:46:50',1241783,'application/pdf','415d3064-a465-4813-8f42-d6f1aa9b87c0','00a6d2f6-c53a-4685-a537-ea7816d8bf1b','e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855','d8785081-e2bf-4ced-97fa-3b785de3abc6',100),('IMG_0949.jpeg','2025-08-05 06:40:57',4827466,'image/jpeg','415d3064-a465-4813-8f42-d6f1aa9b87c0','084903ee-0755-46f0-933f-57f770019a5f','e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855','d8785081-e2bf-4ced-97fa-3b785de3abc6',11),('java_error_in_idea.hprof','2025-08-04 19:26:02',837500268,'application/octet-stream','415d3064-a465-4813-8f42-d6f1aa9b87c0','097b4017-333d-400f-b583-612482f466bb','e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855','d8785081-e2bf-4ced-97fa-3b785de3abc6',222),('目前B站最全最细的excel零基础全套教程，2024最新版，包含所有干货！七天就能从小白到大神！少走99%的弯路！存下吧！很难找全的！ - 001 - 1.Excel 入门.mp4','2025-08-05 05:01:10',17030466,'video/mp4','415d3064-a465-4813-8f42-d6f1aa9b87c0','584d36cd-e78f-414f-a433-7c94e30fb0c5','e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855','d8785081-e2bf-4ced-97fa-3b785de3abc6',33),('恋爱日记WebSite.xd','2025-08-05 08:57:33',696460343,'application/octet-stream','415d3064-a465-4813-8f42-d6f1aa9b87c0','5e798c67-91ac-4c06-81bd-b92331e63825','e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855','d8785081-e2bf-4ced-97fa-3b785de3abc6',45),('THwatch.7z','2025-08-05 04:48:46',30849047,'application/x-7z-compressed','415d3064-a465-4813-8f42-d6f1aa9b87c0','6304a342-565e-4062-8886-7d379dd52c27','e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855','d8785081-e2bf-4ced-97fa-3b785de3abc6',343),('tiantianshop-app-ui.xd','2025-08-05 06:34:35',2402141,'application/octet-stream','415d3064-a465-4813-8f42-d6f1aa9b87c0','bd89b6d0-1882-42d5-8766-a271dcaaf101','e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855','d8785081-e2bf-4ced-97fa-3b785de3abc6',56),('Altium Designer 25.3.3.rar','2025-08-05 09:21:06',3211461602,'application/octet-stream','415d3064-a465-4813-8f42-d6f1aa9b87c0','c24bd276-8459-4f25-b711-fc7ce41b77d1','e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855','d8785081-e2bf-4ced-97fa-3b785de3abc6',34),('2024-11-19-raspios-bookworm-arm64-full.img.xz','2025-08-05 09:25:22',3098494844,'application/octet-stream','415d3064-a465-4813-8f42-d6f1aa9b87c0','dccd57a7-83fe-42d2-8107-18007c16e93b','e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855','d8785081-e2bf-4ced-97fa-3b785de3abc6',43);
/*!40000 ALTER TABLE `pcd_file_info_table` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `pcd_folder_info_table`
--

DROP TABLE IF EXISTS `pcd_folder_info_table`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `pcd_folder_info_table` (
  `folder_uploaded_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `folder_author_id` varchar(36) NOT NULL,
  `folder_id` varchar(36) NOT NULL,
  `folder_lists_info` json NOT NULL,
  `folder_name` varchar(200) NOT NULL,
  PRIMARY KEY (`folder_id`),
  KEY `fk_folder_info_user_info` (`folder_author_id`),
  CONSTRAINT `fk_folder_info_user_info` FOREIGN KEY (`folder_author_id`) REFERENCES `pcd_user_info_table` (`user_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `pcd_folder_info_table`
--

LOCK TABLES `pcd_folder_info_table` WRITE;
/*!40000 ALTER TABLE `pcd_folder_info_table` DISABLE KEYS */;
/*!40000 ALTER TABLE `pcd_folder_info_table` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `pcd_login_token_mange_table`
--

DROP TABLE IF EXISTS `pcd_login_token_mange_table`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `pcd_login_token_mange_table` (
  `login_token_id` varchar(36) NOT NULL,
  `login_token_starting_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `login_token_endding_time` timestamp NOT NULL,
  `login_token_user_id` varchar(36) NOT NULL,
  PRIMARY KEY (`login_token_id`),
  KEY `fk_login_token_user_info` (`login_token_user_id`),
  CONSTRAINT `fk_login_token_user_info` FOREIGN KEY (`login_token_user_id`) REFERENCES `pcd_user_info_table` (`user_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `pcd_login_token_mange_table`
--

LOCK TABLES `pcd_login_token_mange_table` WRITE;
/*!40000 ALTER TABLE `pcd_login_token_mange_table` DISABLE KEYS */;
/*!40000 ALTER TABLE `pcd_login_token_mange_table` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `pcd_sharing_Link_mange_table`
--

DROP TABLE IF EXISTS `pcd_sharing_Link_mange_table`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `pcd_sharing_Link_mange_table` (
  `sharing_link_id` varchar(36) NOT NULL,
  `sharing_link_path` varchar(512) NOT NULL,
  `sharing_link_file_id` varchar(36) NOT NULL,
  `sharing_link_valid_starting_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `sharing_link_valid_endding_time` timestamp NULL DEFAULT NULL,
  `sharing_link_password` varchar(60) DEFAULT NULL,
  PRIMARY KEY (`sharing_link_id`),
  KEY `fk_sharing_link_file_info` (`sharing_link_file_id`),
  CONSTRAINT `fk_sharing_link_file_info` FOREIGN KEY (`sharing_link_file_id`) REFERENCES `pcd_file_info_table` (`file_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `pcd_sharing_Link_mange_table`
--

LOCK TABLES `pcd_sharing_Link_mange_table` WRITE;
/*!40000 ALTER TABLE `pcd_sharing_Link_mange_table` DISABLE KEYS */;
/*!40000 ALTER TABLE `pcd_sharing_Link_mange_table` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `pcd_upload_chunks_table`
--

DROP TABLE IF EXISTS `pcd_upload_chunks_table`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `pcd_upload_chunks_table` (
  `chunk_uploads_id` varchar(36) NOT NULL,
  `chunk_index` int NOT NULL,
  `chunk_status` enum('pending','uploading','uploaded','failed') DEFAULT 'pending',
  `chunk_storage_path` varchar(512) NOT NULL,
  `chunk_uploaded_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `chunk_checksum` varchar(256) NOT NULL,
  PRIMARY KEY (`chunk_uploads_id`,`chunk_index`),
  CONSTRAINT `fk_chunks_uploads_session` FOREIGN KEY (`chunk_uploads_id`) REFERENCES `pcd_uploads_session_table` (`uploads_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `pcd_upload_chunks_table`
--

LOCK TABLES `pcd_upload_chunks_table` WRITE;
/*!40000 ALTER TABLE `pcd_upload_chunks_table` DISABLE KEYS */;
/*!40000 ALTER TABLE `pcd_upload_chunks_table` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `pcd_uploads_session_table`
--

DROP TABLE IF EXISTS `pcd_uploads_session_table`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `pcd_uploads_session_table` (
  `uploads_id` varchar(36) NOT NULL,
  `uploads_user_id` varchar(36) NOT NULL,
  `uploads_total_chunks` int NOT NULL,
  `uploads_starting_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `uploads_endding_time` timestamp NOT NULL,
  `uploads_file_size` bigint NOT NULL,
  `uploads_file_checksum` varchar(256) NOT NULL,
  `uploads_chunks_max_size` int NOT NULL,
  `uploads_file_name` varchar(150) NOT NULL,
  `uploads_file_type` varchar(60) NOT NULL,
  `uploads_node_id` varchar(36) NOT NULL,
  `uploads_status` enum('uploading','merging','completed','failed') DEFAULT 'uploading',
  PRIMARY KEY (`uploads_id`),
  KEY `fk_uploads_session_user_info` (`uploads_user_id`),
  KEY `fk_uploads_session_directory_tree` (`uploads_node_id`),
  CONSTRAINT `fk_uploads_session_directory_tree` FOREIGN KEY (`uploads_node_id`) REFERENCES `pcd_directory_tree_table` (`node_id`) ON DELETE CASCADE,
  CONSTRAINT `fk_uploads_session_user_info` FOREIGN KEY (`uploads_user_id`) REFERENCES `pcd_user_info_table` (`user_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `pcd_uploads_session_table`
--

LOCK TABLES `pcd_uploads_session_table` WRITE;
/*!40000 ALTER TABLE `pcd_uploads_session_table` DISABLE KEYS */;
/*!40000 ALTER TABLE `pcd_uploads_session_table` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `pcd_user_info_table`
--

DROP TABLE IF EXISTS `pcd_user_info_table`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `pcd_user_info_table` (
  `user_name` varchar(120) NOT NULL,
  `user_id` varchar(36) NOT NULL,
  `user_phone_number` varchar(50) NOT NULL,
  `user_image_path` varchar(512) DEFAULT NULL,
  `user_password` varchar(70) NOT NULL,
  `user_account` varchar(70) NOT NULL,
  `user_email` varchar(70) DEFAULT NULL,
  PRIMARY KEY (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `pcd_user_info_table`
--

LOCK TABLES `pcd_user_info_table` WRITE;
/*!40000 ALTER TABLE `pcd_user_info_table` DISABLE KEYS */;
INSERT INTO `pcd_user_info_table` VALUES ('XiaoMo','415d3064-a465-4813-8f42-d6f1aa9b87c0','15777446691',NULL,'20070315mwz','pcd_18181999067','1773172144@qq.com');
/*!40000 ALTER TABLE `pcd_user_info_table` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-05-13 21:56:15
