package org.openelisglobal.panelitem.service;

import java.util.List;
import org.openelisglobal.common.exception.LIMSRuntimeException;
import org.openelisglobal.common.service.BaseObjectService;
import org.openelisglobal.panel.valueholder.Panel;
import org.openelisglobal.panelitem.valueholder.PanelItem;
import org.openelisglobal.test.valueholder.Test;

public interface PanelItemService extends BaseObjectService<PanelItem, String> {
    void getData(PanelItem panelItem);

    Integer getTotalPanelItemCount();

    List<PanelItem> getPanelItemsForPanelAndItemList(String panelId, List<Integer> testList);

    List<PanelItem> getPageOfPanelItems(int startingRecNo);

    boolean getDuplicateSortOrderForPanel(PanelItem panelItem);

    List<PanelItem> getPanelItemByTestId(String id);

    List<PanelItem> getAllPanelItems();

    List<PanelItem> getPanelItems(String filter);

    List<PanelItem> getPanelItemsForPanel(String panelId);

    /**
     * Replaces a panel's membership.
     *
     * <p>
     * {@code oclManaged} says who this write belongs to and has no default on
     * purpose. Passing {@code false} records the panel's membership as lab-owned,
     * which stops the OCL importer from ever overwriting it. Passing {@code true}
     * is for the importer itself. Making it a required parameter means a new call
     * site cannot silently inherit the wrong answer: the compiler asks.
     */
    void updatePanelItems(List<PanelItem> panelItems, Panel panel, boolean updatePanel, String currentUser,
            List<Test> newTests, boolean oclManaged);

    boolean duplicatePanelItemExists(PanelItem panelItem) throws LIMSRuntimeException;
}
