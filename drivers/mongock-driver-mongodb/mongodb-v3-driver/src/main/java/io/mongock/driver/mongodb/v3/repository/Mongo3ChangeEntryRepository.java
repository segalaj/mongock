package io.mongock.driver.mongodb.v3.repository;

import io.mongock.driver.api.entry.ChangeEntry;
import io.mongock.driver.api.entry.ChangeState;
import io.mongock.driver.api.entry.ExecutedChangeEntry;
import io.mongock.driver.core.entry.ChangeEntryRepositoryWithEntity;
import io.mongock.api.exception.MongockException;

import com.mongodb.client.ClientSession;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Accumulators;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.Sorts;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.result.UpdateResult;
import io.mongock.driver.api.entry.ChangeType;
import org.bson.Document;
import org.bson.conversions.Bson;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class Mongo3ChangeEntryRepository extends Mongo3RepositoryBase<ChangeEntry> implements ChangeEntryRepositoryWithEntity<Document> {

  protected static String KEY_EXECUTION_ID;
  protected static String KEY_CHANGE_ID;
  protected static String KEY_STATE;
  protected static String KEY_TYPE;
  protected static String KEY_AUTHOR;
  protected static String KEY_TIMESTAMP;
  protected static String KEY_CHANGELOG_CLASS;
  protected static String KEY_CHANGESET_METHOD;
  protected static String KEY_EXECUTION_MILLIS;
  protected static String KEY_EXECUTION_HOSTNAME;
  protected static String KEY_METADATA;

  private ClientSession clientSession;

  static {
    try {
       Field field = ChangeEntry.class.getDeclaredField("executionId");
       field.setAccessible(true);
      KEY_EXECUTION_ID = field.getAnnotation(io.mongock.utils.field.Field.class).value();

      field = ChangeEntry.class.getDeclaredField("changeId");
      field.setAccessible(true);
      KEY_CHANGE_ID = field.getAnnotation(io.mongock.utils.field.Field.class).value();

      field = ChangeEntry.class.getDeclaredField("state");
      field.setAccessible(true);
      KEY_STATE = field.getAnnotation(io.mongock.utils.field.Field.class).value();
      
      field = ChangeEntry.class.getDeclaredField("type");
      field.setAccessible(true);
      KEY_TYPE = field.getAnnotation(io.mongock.utils.field.Field.class).value();

      field = ChangeEntry.class.getDeclaredField("author");
      field.setAccessible(true);
      KEY_AUTHOR = field.getAnnotation(io.mongock.utils.field.Field.class).value();

      field = ChangeEntry.class.getDeclaredField("timestamp");
      field.setAccessible(true);
      KEY_TIMESTAMP = field.getAnnotation(io.mongock.utils.field.Field.class).value();

      field = ChangeEntry.class.getDeclaredField("changeLogClass");
      field.setAccessible(true);
      KEY_CHANGELOG_CLASS = field.getAnnotation(io.mongock.utils.field.Field.class).value();

      field = ChangeEntry.class.getDeclaredField("changeSetMethod");
      field.setAccessible(true);
      KEY_CHANGESET_METHOD = field.getAnnotation(io.mongock.utils.field.Field.class).value();
      
      field = ChangeEntry.class.getDeclaredField("metadata");
      field.setAccessible(true);
      KEY_METADATA = field.getAnnotation(io.mongock.utils.field.Field.class).value();
      
      field = ChangeEntry.class.getDeclaredField("executionMillis");
      field.setAccessible(true);
      KEY_EXECUTION_MILLIS = field.getAnnotation(io.mongock.utils.field.Field.class).value();
      
      field = ChangeEntry.class.getDeclaredField("executionHostname");
      field.setAccessible(true);
      KEY_EXECUTION_HOSTNAME = field.getAnnotation(io.mongock.utils.field.Field.class).value();
    } catch (NoSuchFieldException e) {
      throw new MongockException(e);
    }
  }

  public Mongo3ChangeEntryRepository(MongoCollection<Document> collection) {
    this(collection, ReadWriteConfiguration.getDefault());
  }

  public Mongo3ChangeEntryRepository(MongoCollection<Document> collection,  ReadWriteConfiguration readWriteConfiguration) {
    super(collection, new String[]{KEY_EXECUTION_ID, KEY_AUTHOR, KEY_CHANGE_ID}, readWriteConfiguration);
  }
  
  @Override
  public boolean isAlreadyExecuted(String changeSetId, String author) throws MongockException {
    Document entry = collection.find(buildSearchQueryDBObject(changeSetId, author)).sort(Sorts.descending(KEY_TIMESTAMP)).first();    
    if (entry != null && !entry.isEmpty()) {
      String entryState = entry.getString(KEY_STATE);
      return entryState == null ||
             entryState.isEmpty() ||
             entryState.equals(ChangeState.EXECUTED.name());
    }
    else {
      return false;
    }
  }
  
  @Override
  public List<ExecutedChangeEntry> getExecuted() throws MongockException {
    Bson matchExecutedOrRolledBack = Aggregates.match(Filters.or(
        Filters.eq(KEY_STATE, ChangeState.EXECUTED.name()),
        Filters.eq(KEY_STATE, ChangeState.ROLLED_BACK.name()),
        Filters.eq(KEY_STATE, null),
        Filters.exists(KEY_STATE, false)
    ));
    Bson previousSort = Aggregates.sort(Sorts.descending(KEY_TIMESTAMP));
    Bson group = Aggregates.group(Projections.fields(Filters.eq(KEY_CHANGE_ID, "$" + KEY_CHANGE_ID), Filters.eq(KEY_AUTHOR, "$" + KEY_AUTHOR)),
                                  Accumulators.first(KEY_CHANGE_ID, "$" + KEY_CHANGE_ID),
                                  Accumulators.first(KEY_AUTHOR, "$" + KEY_AUTHOR),
                                  Accumulators.first(KEY_STATE, "$" + KEY_STATE),
                                  Accumulators.first(KEY_TIMESTAMP, "$" + KEY_TIMESTAMP),
                                  Accumulators.first(KEY_CHANGELOG_CLASS, "$" + KEY_CHANGELOG_CLASS),
                                  Accumulators.first(KEY_CHANGESET_METHOD, "$" + KEY_CHANGESET_METHOD));
    Bson projection = Aggregates.project(Projections.fields(Projections.include(KEY_CHANGE_ID, KEY_AUTHOR, KEY_STATE, KEY_TIMESTAMP, KEY_CHANGELOG_CLASS, KEY_CHANGESET_METHOD), Projections.excludeId()));
    Bson finalSort = Aggregates.sort(Sorts.ascending(KEY_TIMESTAMP));
    Bson matchOnlyExecuted = Aggregates.match(Filters.or(
        Filters.eq(KEY_STATE, ChangeState.EXECUTED.name()),
        Filters.eq(KEY_STATE, null),
        Filters.exists(KEY_STATE, false)
    ));
    
    return collection.aggregate(Arrays.asList(matchExecutedOrRolledBack, previousSort, group, projection, matchOnlyExecuted, finalSort))
                     .into(new ArrayList<>())
                     .stream()
                     .map(entry -> new ExecutedChangeEntry(entry.getString(KEY_CHANGE_ID),
                                                           entry.getString(KEY_AUTHOR),
                                                           entry.getDate(KEY_TIMESTAMP),
                                                           entry.getString(KEY_CHANGELOG_CLASS),
                                                           entry.getString(KEY_CHANGESET_METHOD)))
                     .collect(Collectors.toList());
  }

  @Override
  public List<ChangeEntry> getEntriesLog() {
    return collection.find()
            .into(new ArrayList<>())
            .stream()
            .map(entry -> new ChangeEntry(
                                  entry.getString(KEY_EXECUTION_ID),
                                  entry.getString(KEY_CHANGE_ID),
                                  entry.getString(KEY_AUTHOR),
                                  entry.getDate(KEY_TIMESTAMP),
                                  entry.getString(KEY_STATE) != null 
                                          ? ChangeState.valueOf(entry.getString(KEY_STATE)) 
                                          : null,
                                  entry.getString(KEY_TYPE) != null 
                                          ? ChangeType.valueOf(entry.getString(KEY_TYPE)) 
                                          : null,
                                  entry.getString(KEY_CHANGELOG_CLASS),
                                  entry.getString(KEY_CHANGESET_METHOD),
                                  entry.getLong(KEY_EXECUTION_MILLIS),
                                  entry.getString(KEY_EXECUTION_HOSTNAME),
                                  entry.get(KEY_METADATA)))
            .collect(Collectors.toList());
  }

  public void setClientSession(ClientSession clientSession) {
    this.clientSession = clientSession;
  }

  public void clearClientSession() {
    setClientSession(null);
  }

  private Optional<ClientSession> getClientSession() {
    return Optional.ofNullable(clientSession);
  }


  @Override
  public void saveOrUpdate(ChangeEntry changeEntry) throws MongockException {
    Bson filter = Filters.and(
        Filters.eq(KEY_EXECUTION_ID, changeEntry.getExecutionId()),
        Filters.eq(KEY_CHANGE_ID, changeEntry.getChangeId()),
        Filters.eq(KEY_AUTHOR, changeEntry.getAuthor())
    );

    Document document = collection.find(filter).first();
    if (document != null) {
      toEntity(changeEntry).forEach(document::put);

      UpdateResult result = getClientSession()
          .map(clientSession -> collection.updateOne(clientSession, filter, new Document("$set", document), new UpdateOptions().upsert(true)))
          .orElseGet(() -> collection.updateOne(filter, new Document("$set", document), new UpdateOptions().upsert(true)));
    } else {
      if(getClientSession().isPresent()) {
        collection.insertOne(getClientSession().get(), toEntity(changeEntry));
      } else {
        collection.insertOne(toEntity(changeEntry));
      }
    }
  }


  /**
   * Check if a changeSet with given changeSetId and author and
   * (state == EXECUTED OR state == ROLLED_BACK OR state == null OR estate doesn't exists)
   * @param changeSetId changeSetId
   * @param author author
   * @return query filter object
   */
  protected Bson buildSearchQueryDBObject(String changeSetId, String author) {
    Bson executedStateOrNoExisting = Filters.or(
        Filters.eq(KEY_STATE, ChangeState.EXECUTED.name()),
        Filters.eq(KEY_STATE, ChangeState.ROLLED_BACK.name()),
        Filters.eq(KEY_STATE, null),
        Filters.exists(KEY_STATE, false)
    );
    return Filters.and(
        Filters.eq(KEY_CHANGE_ID, changeSetId),
        Filters.eq(KEY_AUTHOR, author),
        executedStateOrNoExisting
    );
  }
}
