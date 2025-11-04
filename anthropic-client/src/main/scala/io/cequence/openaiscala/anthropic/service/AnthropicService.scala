package io.cequence.openaiscala.anthropic.service

import akka.NotUsed
import akka.stream.scaladsl.Source
import akka.util.ByteString
import io.cequence.openaiscala.anthropic.domain.{
  FileDeleteResponse,
  FileListResponse,
  FileMetadata,
  Message
}
import io.cequence.openaiscala.anthropic.domain.response.{
  ContentBlockDelta,
  CreateMessageResponse
}
import io.cequence.openaiscala.anthropic.domain.settings.AnthropicCreateMessageSettings
import io.cequence.openaiscala.anthropic.domain.skills.{
  DeleteSkillResponse,
  DeleteSkillVersionResponse,
  ListSkillVersionsResponse,
  ListSkillsResponse,
  Skill,
  SkillSource,
  SkillVersion
}
import io.cequence.wsclient.service.CloseableService

import java.io.File
import scala.concurrent.Future

trait AnthropicService extends CloseableService with AnthropicServiceConsts {

  /**
   * Creates a message.
   *
   * Send a structured list of input messages with text and/or image content, and the model
   * will generate the next message in the conversation.
   *
   * The Messages API can be used for for either single queries or stateless multi-turn
   * conversations.
   *
   * @param messages
   *   A list of messages comprising the conversation so far.
   * @param settings
   * @return
   *   create message response
   * @see
   *   <a href="https://docs.anthropic.com/claude/reference/messages_post">Anthropic Doc</a>
   */
  def createMessage(
    messages: Seq[Message],
    settings: AnthropicCreateMessageSettings = DefaultSettings.CreateMessage
  ): Future[CreateMessageResponse]

  /**
   * Creates a message (streamed version).
   *
   * Send a structured list of input messages with text and/or image content, and the model
   * will generate the next message in the conversation.
   *
   * The Messages API can be used for for either single queries or stateless multi-turn
   * conversations.
   *
   * @param messages
   *   A list of messages comprising the conversation so far.
   * @param settings
   * @return
   *   create message response
   * @see
   *   <a href="https://docs.anthropic.com/claude/reference/messages_post">Anthropic Doc</a>
   */
  def createMessageStreamed(
    messages: Seq[Message],
    settings: AnthropicCreateMessageSettings = DefaultSettings.CreateMessage
  ): Source[ContentBlockDelta, NotUsed]

  /**
   * Creates a custom skill.
   *
   * Skills allow you to define custom tools and behaviors that Claude can use. All files must
   * be in the same top-level directory and must include a SKILL.md file at the root of that
   * directory.
   *
   * @param displayTitle
   *   Display title for the skill. This is a human-readable label that is not included in the
   *   prompt sent to the model.
   * @param files
   *   Files to upload for the skill as tuples of (File, filename). The filename should include
   *   the directory structure, e.g., "skill-name/SKILL.md". All files must be in the same
   *   top-level directory and must include a SKILL.md file at the root of that directory.
   * @return
   *   The created skill
   * @see
   *   <a href="https://docs.claude.com/en/api/skills/create-skill">Anthropic Skills Doc</a>
   */
  def createSkill(
    displayTitle: Option[String] = None,
    files: Seq[(File, String)]
  ): Future[Skill]

  /**
   * Lists all skills.
   *
   * Retrieves a paginated list of all skills in your workspace.
   *
   * @param page
   *   Pagination token for fetching a specific page of results. Pass the value from a previous
   *   response's next_page field to get the next page of results.
   * @param limit
   *   Number of results to return per page. Maximum value is 100. Defaults to 20.
   * @param source
   *   Filter skills by source. If provided, only skills from the specified source will be
   *   returned: "custom" for user-created skills or "anthropic" for Anthropic-created skills.
   * @return
   *   List of skills with pagination information
   * @see
   *   <a href="https://docs.claude.com/en/api/skills/list-skills">Anthropic Skills Doc</a>
   */
  def listSkills(
    page: Option[String] = None,
    limit: Option[Int] = None,
    source: Option[SkillSource] = None
  ): Future[ListSkillsResponse]

  /**
   * Retrieves a specific skill by ID.
   *
   * @param skillId
   *   Unique identifier for the skill.
   * @return
   *   The skill object
   * @see
   *   <a href="https://docs.claude.com/en/api/skills/get-skill">Anthropic Skills Doc</a>
   */
  def getSkill(skillId: String): Future[Skill]

  /**
   * Deletes a custom skill.
   *
   * Only custom skills (created by the user) can be deleted. Anthropic-created skills cannot
   * be deleted.
   *
   * @param skillId
   *   Unique identifier for the skill to delete.
   * @return
   *   Confirmation of deletion
   * @see
   *   <a href="https://docs.claude.com/en/api/skills/delete-skill">Anthropic Skills Doc</a>
   */
  def deleteSkill(skillId: String): Future[DeleteSkillResponse]

  /**
   * Creates a new version of an existing skill.
   *
   * Creates a new version of a skill by uploading files. All files must be in the same
   * top-level directory and must include a SKILL.md file at the root of that directory.
   *
   * @param skillId
   *   Unique identifier for the skill to create a new version for.
   * @param files
   *   Files to upload for the skill version as tuples of (File, filename). The filename should
   *   include the directory structure, e.g., "skill-name/SKILL.md". All files must be in the
   *   same top-level directory and must include a SKILL.md file at the root of that directory.
   * @return
   *   The created skill version
   * @see
   *   <a href="https://docs.claude.com/en/api/skills/create-skill-version">Anthropic Skills
   *   Doc</a>
   */
  def createSkillVersion(
    skillId: String,
    files: Seq[(File, String)]
  ): Future[SkillVersion]

  /**
   * Lists all versions of a skill.
   *
   * Retrieves a paginated list of all versions for a specific skill. Skills can have multiple
   * versions, each identified by a Unix epoch timestamp. The list is sorted in reverse
   * chronological order, with the most recent version first.
   *
   * @param skillId
   *   Unique identifier for the skill whose versions should be retrieved.
   * @param page
   *   Pagination token for fetching a specific page of results. Pass the value from a previous
   *   response's next_page field to get the next page of results.
   * @param limit
   *   Number of results to return per page. Maximum value is 100. Defaults to 20.
   * @return
   *   List of skill versions with pagination information
   * @see
   *   <a href="https://docs.claude.com/en/api/skills/list-skill-versions">Anthropic Skills
   *   Doc</a>
   */
  def listSkillVersions(
    skillId: String,
    page: Option[String] = None,
    limit: Option[Int] = None
  ): Future[ListSkillVersionsResponse]

  /**
   * Retrieves a specific version of a skill.
   *
   * @param skillId
   *   Unique identifier for the skill.
   * @param version
   *   Version identifier for the skill. Each version is identified by a Unix epoch timestamp
   *   (e.g., "1759178010641129").
   * @return
   *   The skill version object
   * @see
   *   <a href="https://docs.claude.com/en/api/skills/get-skill-version">Anthropic Skills
   *   Doc</a>
   */
  def getSkillVersion(
    skillId: String,
    version: String
  ): Future[SkillVersion]

  /**
   * Deletes a specific version of a skill.
   *
   * Only custom skill versions (created by the user) can be deleted. Anthropic-created skill
   * versions cannot be deleted.
   *
   * @param skillId
   *   Unique identifier for the skill.
   * @param version
   *   Version identifier for the skill. Each version is identified by a Unix epoch timestamp
   *   (e.g., "1759178010641129").
   * @return
   *   Confirmation of deletion
   * @see
   *   <a href="https://docs.claude.com/en/api/skills/delete-skill-version">Anthropic Skills
   *   Doc</a>
   */
  def deleteSkillVersion(
    skillId: String,
    version: String
  ): Future[DeleteSkillVersionResponse]

  /**
   * Uploads a file.
   *
   * Upload a file that can be referenced in messages.
   *
   * @param file
   *   The file to upload.
   * @param filename
   *   Optional custom filename to use instead of the file's actual name.
   * @return
   *   Metadata for the uploaded file
   * @see
   *   <a href="https://docs.anthropic.com/en/api/files">Anthropic Files Doc</a>
   */
  def createFile(
    file: File,
    filename: Option[String] = None
  ): Future[FileMetadata]

  /**
   * Lists files within a workspace.
   *
   * Retrieves a paginated list of all files in your workspace.
   *
   * @param beforeId
   *   ID of the object to use as a cursor for pagination. When provided, returns the page of
   *   results immediately before this object.
   * @param afterId
   *   ID of the object to use as a cursor for pagination. When provided, returns the page of
   *   results immediately after this object.
   * @param limit
   *   Number of items to return per page. Defaults to 20. Ranges from 1 to 1000.
   * @return
   *   List of files with pagination information
   * @see
   *   <a href="https://docs.claude.com/en/api/files-list">Anthropic Files Doc</a>
   */
  def listFiles(
    beforeId: Option[String] = None,
    afterId: Option[String] = None,
    limit: Option[Int] = None
  ): Future[FileListResponse]

  /**
   * Retrieves metadata for a specific file.
   *
   * Returns metadata information about a file identified by its ID. Returns None if the file
   * is not found.
   *
   * @param fileId
   *   ID of the file.
   * @return
   *   File metadata if found, None otherwise
   * @see
   *   <a href="https://docs.claude.com/en/api/files-metadata">Anthropic Files Doc</a>
   */
  def getFileMetadata(fileId: String): Future[Option[FileMetadata]]

  /**
   * Downloads the contents of a Claude generated file.
   *
   * Returns the binary content of a file. Returns None if the file is not found.
   *
   * @param fileId
   *   ID of the file to download.
   * @return
   *   File content as byte stream if found, None otherwise
   * @see
   *   <a href="https://docs.claude.com/en/api/files-content">Anthropic Files Doc</a>
   */
  def downloadFile(fileId: String): Future[Option[Source[ByteString, _]]]

  /**
   * Deletes a file.
   *
   * Makes a file inaccessible through the API.
   *
   * @param fileId
   *   ID of the file to delete.
   * @return
   *   Confirmation of deletion with the file ID
   * @see
   *   <a href="https://docs.anthropic.com/en/api/files">Anthropic Files Doc</a>
   */
  def deleteFile(fileId: String): Future[FileDeleteResponse]
}
