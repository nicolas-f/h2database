/*
 * Copyright 2004-2009 H2 Group. Multiple-Licensed under the H2 License,
 * Version 1.0, and under the Eclipse Public License, Version 1.0
 * (http://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h2.index;

import java.sql.SQLException;
import org.h2.constant.ErrorCode;
import org.h2.constant.SysProperties;
import org.h2.engine.Constants;
import org.h2.engine.Session;
import org.h2.message.Message;
import org.h2.result.Row;
import org.h2.result.SearchRow;
import org.h2.store.DataPage;
import org.h2.store.PageStore;
import org.h2.store.Record;
import org.h2.table.Column;
import org.h2.table.IndexColumn;
import org.h2.table.TableData;
import org.h2.value.Value;
import org.h2.value.ValueLob;

/**
 * This is the most common type of index, a b tree index.
 * Only the data of the indexed columns are stored in the index.
 */
public class PageBtreeIndex extends BaseIndex {

    private PageStore store;
    private TableData tableData;
    private int headPos;
    private long rowCount;

    public PageBtreeIndex(TableData table, int id, String indexName, IndexColumn[] columns,
            IndexType indexType, int headPos) throws SQLException {
        initBaseIndex(table, id, indexName, columns, indexType);
        // trace.setLevel(TraceSystem.DEBUG);
        if (database.isMultiVersion()) {
            int todoMvcc;
        }
        tableData = table;
        if (!database.isPersistent() || id < 0) {
            int todo;
            return;
        }
        this.store = database.getPageStore();
        if (headPos == Index.EMPTY_HEAD) {
            // new table
            headPos = store.allocatePage();
            PageBtreeLeaf root = new PageBtreeLeaf(this, headPos, Page.ROOT, store.createDataPage());
            store.updateRecord(root, true, root.data);
        } else if (store.isNew()) {
            // the system table for a new database
            PageBtreeLeaf root = new PageBtreeLeaf(this, headPos, Page.ROOT, store.createDataPage());
            store.updateRecord(root, true, root.data);
        } else {
            rowCount = getPage(headPos).getRowCount();
            int reuseKeysIfManyDeleted;
        }
        this.headPos = headPos;
        if (trace.isDebugEnabled()) {
            trace.debug("open " + rowCount);
        }
    }

    public int getHeadPos() {
        return headPos;
    }

    public void add(Session session, Row row) throws SQLException {
        if (trace.isDebugEnabled()) {
            trace.debug("add " + row.getPos());
        }
        if (tableData.getContainsLargeObject()) {
            for (int i = 0; i < row.getColumnCount(); i++) {
                Value v = row.getValue(i);
                Value v2 = v.link(database, getId());
                if (v2.isLinked()) {
                    session.unlinkAtCommitStop(v2);
                }
                if (v != v2) {
                    row.setValue(i, v2);
                }
            }
        }
        while (true) {
            PageBtree root = getPage(headPos);
            int splitPoint = root.addRow(session, row);
            if (splitPoint == 0) {
                break;
            }
            if (trace.isDebugEnabled()) {
                trace.debug("split " + splitPoint);
            }
            SearchRow pivot = root.getRow(session, splitPoint - 1);
            PageBtree page1 = root;
            PageBtree page2 = root.split(session, splitPoint);
            int rootPageId = root.getPageId();
            int id = store.allocatePage();
            page1.setPageId(id);
            page1.setParentPageId(headPos);
            page2.setParentPageId(headPos);
            PageBtreeNode newRoot = new PageBtreeNode(this, rootPageId, Page.ROOT, store.createDataPage());
            newRoot.init(page1, pivot, page2);
            store.updateRecord(page1, true, page1.data);
            store.updateRecord(page2, true, page2.data);
            store.updateRecord(newRoot, true, null);
            root = newRoot;
        }
        rowCount++;
        store.logAddOrRemoveRow(session, tableData.getId(), row, true);
    }

    /**
     * Read the given page.
     *
     * @param id the page id
     * @return the page
     */
    PageBtree getPage(int id) throws SQLException {
        Record rec = store.getRecord(id);
        if (rec != null) {
            return (PageBtree) rec;
        }
        DataPage data = store.readPage(id);
        data.reset();
        int parentPageId = data.readInt();
        int type = data.readByte() & 255;
        PageBtree result;
        switch (type & ~Page.FLAG_LAST) {
        case Page.TYPE_BTREE_LEAF:
            result = new PageBtreeLeaf(this, id, parentPageId, data);
            break;
        case Page.TYPE_BTREE_NODE:
            result = new PageBtreeNode(this, id, parentPageId, data);
            break;
        case Page.TYPE_EMPTY:
            PageBtreeLeaf empty = new PageBtreeLeaf(this, id, parentPageId, data);
            return empty;
        default:
            throw Message.getSQLException(ErrorCode.FILE_CORRUPTED_1, "page=" + id + " type=" + type);
        }
        result.read();
        return result;
    }

    public boolean canGetFirstOrLast() {
        return false;
    }

    public Cursor findNext(Session session, SearchRow first, SearchRow last) throws SQLException {
        return find(session, first, true, last);
    }

    public Cursor find(Session session, SearchRow first, SearchRow last) throws SQLException {
        return find(session, first, false, last);
    }

    private Cursor find(Session session, SearchRow first, boolean bigger, SearchRow last) throws SQLException {
        if (SysProperties.CHECK && store == null) {
            throw Message.getSQLException(ErrorCode.OBJECT_CLOSED);
        }
        PageBtree root = getPage(headPos);
        PageBtreeCursor cursor = new PageBtreeCursor(session, this, last);
        root.find(cursor, first, bigger);
        return cursor;
    }

    public Cursor findFirstOrLast(Session session, boolean first) throws SQLException {
        throw Message.getUnsupportedException();
    }

    public double getCost(Session session, int[] masks) throws SQLException {
        long cost = 10 * (tableData.getRowCountApproximation() + Constants.COST_ROW_OFFSET);
        return cost;
    }

    public boolean needRebuild() {
        return false;
    }

    public void remove(Session session, Row row) throws SQLException {
        if (trace.isDebugEnabled()) {
            trace.debug("remove " + row.getPos());
        }
        if (tableData.getContainsLargeObject()) {
            for (int i = 0; i < row.getColumnCount(); i++) {
                Value v = row.getValue(i);
                if (v.isLinked()) {
                    session.unlinkAtCommit((ValueLob) v);
                }
            }
        }
        int invalidateRowCount;
        // setChanged(session);
        if (rowCount == 1) {
            int todoMaybeImprove;
            removeAllRows();
        } else {
            PageBtree root = getPage(headPos);
            root.remove(session, row);
            rowCount--;
            int todoReuseKeys;
        }
        store.logAddOrRemoveRow(session, tableData.getId(), row, false);
    }

    public void remove(Session session) throws SQLException {
        if (trace.isDebugEnabled()) {
            trace.debug("remove");
        }
        int todo;
    }

    public void truncate(Session session) throws SQLException {
        if (trace.isDebugEnabled()) {
            trace.debug("truncate");
        }
        removeAllRows();
        if (tableData.getContainsLargeObject() && tableData.getPersistent()) {
            ValueLob.removeAllForTable(database, table.getId());
        }
        tableData.setRowCount(0);
    }

    private void removeAllRows() throws SQLException {
        store.removeRecord(headPos);
        int todoLogOldData;
        int freePages;
        PageBtreeLeaf root = new PageBtreeLeaf(this, headPos, Page.ROOT, store.createDataPage());
        store.updateRecord(root, true, null);
        rowCount = 0;
    }

    public void checkRename() throws SQLException {
        throw Message.getUnsupportedException();
    }

    /**
     * Get a row from the data file.
     *
     * @param session the session
     * @param key the row key
     * @return the row
     */
    public Row getRow(Session session, int key) throws SQLException {
        return tableData.getRow(session, key);
    }

    PageStore getPageStore() {
        return store;
    }

    /**
     * Read a row from the data page at the given position.
     *
     * @param data the data page
     * @return the row
     */
    Row readRow(DataPage data) throws SQLException {
        return tableData.readRow(data);
    }

    public long getRowCountApproximation() {
        return rowCount;
    }

    public long getRowCount(Session session) {
        return rowCount;
    }

    public void close(Session session) throws SQLException {
        if (trace.isDebugEnabled()) {
            trace.debug("close");
        }
        store = null;
        int writeRowCount;
    }

    SearchRow readRow(DataPage data, int offset) throws SQLException {
        data.setPos(offset);
        SearchRow row = table.getTemplateSimpleRow(columns.length == 1);
        row.setPos(data.readInt());
        for (int i = 0; i < columns.length; i++) {
            int idx = columns[i].getColumnId();
            row.setValue(idx, data.readValue());
        }
        return row;
    }

    /**
     * Get the size of a row (only the part that is stored in the index).
     *
     * @param dummy a dummy data page to calculate the size
     * @param row the row
     * @return the number of bytes
     */
    int getRowSize(DataPage dummy, SearchRow row) throws SQLException {
        int rowsize = DataPage.LENGTH_INT;
        for (int j = 0; j < columns.length; j++) {
            Value v = row.getValue(columns[j].getColumnId());
            rowsize += dummy.getValueLen(v);
        }
        return rowsize;
    }

}
