CREATE UNIQUE INDEX uk_event_relationship_edge_event_from_to
    ON event_relationship_edge (event_id, from_char_id, to_char_id);
