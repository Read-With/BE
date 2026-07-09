SET @event_relationship_edge_index_exists = (
    SELECT COUNT(1)
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'event_relationship_edge'
      AND index_name = 'uk_event_relationship_edge_event_from_to'
);

SET @event_relationship_edge_index_sql = IF(
    @event_relationship_edge_index_exists = 0,
    'CREATE UNIQUE INDEX uk_event_relationship_edge_event_from_to ON event_relationship_edge (event_id, from_char_id, to_char_id)',
    'SELECT 1'
);

PREPARE event_relationship_edge_index_stmt FROM @event_relationship_edge_index_sql;
EXECUTE event_relationship_edge_index_stmt;
DEALLOCATE PREPARE event_relationship_edge_index_stmt;
