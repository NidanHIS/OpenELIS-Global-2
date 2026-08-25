package org.openelisglobal.testconfiguration.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.openelisglobal.common.controller.BaseController;
import org.openelisglobal.common.exception.LIMSRuntimeException;
import org.openelisglobal.common.log.LogEvent;
import org.openelisglobal.common.services.DisplayListService;
import org.openelisglobal.localization.service.LocalizationService;
import org.openelisglobal.localization.valueholder.Localization;
import org.openelisglobal.panel.service.PanelService;
import org.openelisglobal.panel.valueholder.Panel;
import org.openelisglobal.systemmodule.service.SystemModuleService;
import org.openelisglobal.systemmodule.valueholder.SystemModule;
import org.openelisglobal.testconfiguration.form.PanelRenameEntryForm;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.ModelAndView;

@Controller
public class PanelRenameEntryController extends BaseController {

    private static final String[] ALLOWED_FIELDS = new String[] { "panelId", "nameEnglish", "nameFrench" };

    @Autowired
    PanelService panelService;
    @Autowired
    LocalizationService localizationService;

    @Autowired
    private SystemModuleService systemModuleService;

    @InitBinder
    public void initBinder(WebDataBinder binder) {
        binder.setAllowedFields(ALLOWED_FIELDS);
    }

    @RequestMapping(value = "/PanelRenameEntry", method = RequestMethod.GET)
    public ModelAndView showPanelRenameEntry(HttpServletRequest request) {
        PanelRenameEntryForm form = new PanelRenameEntryForm();
        form.setPanelList(DisplayListService.getInstance().getList(DisplayListService.ListType.PANELS));

        return findForward(FWD_SUCCESS, form);
    }

    @Override
    protected String findLocalForward(String forward) {
        if (FWD_SUCCESS.equals(forward)) {
            return "panelRenameDefinition";
        } else if (FWD_SUCCESS_INSERT.equals(forward)) {
            return "redirect:/PanelRenameEntry";
        } else if (FWD_FAIL_INSERT.equals(forward)) {
            return "panelRenameDefinition";
        } else {
            return "PageNotFound";
        }
    }

    @RequestMapping(value = "/PanelRenameEntry", method = RequestMethod.POST)
    public ModelAndView updatePanelRenameEntry(HttpServletRequest request,
            @ModelAttribute("form") @Valid PanelRenameEntryForm form, BindingResult result) {
        if (result.hasErrors()) {
            saveErrors(result);
            form.setPanelList(DisplayListService.getInstance().getList(DisplayListService.ListType.PANELS));
            return findForward(FWD_FAIL_INSERT, form);
        }

        String panelId = form.getPanelId();
        String nameEnglish = form.getNameEnglish();
        String nameFrench = form.getNameFrench();
        String userId = getSysUserId(request);

        updatePanelNames(panelId, nameEnglish, nameFrench, userId);

        return findForward(FWD_SUCCESS_INSERT, form);
    }

    private void updatePanelNames(String panelId, String nameEnglish, String nameFrench, String userId) {
        Panel panel = panelService.getPanelById(panelId);

        if (panel != null) {
            String trimmedEnglish = nameEnglish != null ? nameEnglish.trim() : "";
            String trimmedFrench = nameFrench != null ? nameFrench.trim() : "";

            Localization name = panel.getLocalization();
            String oldEnglishName = null;
            if (name != null) {
                oldEnglishName = name.getEnglish();
                name.setEnglish(trimmedEnglish);
                name.setFrench(trimmedFrench);
                name.setSysUserId(userId);

                try {
                    localizationService.update(name);
                } catch (LIMSRuntimeException e) {
                    LogEvent.logDebug(e);
                }
            } else if (panel.getPanelName() != null && !panel.getPanelName().trim().isEmpty()) {
                oldEnglishName = panel.getPanelName();
            }

            // Synchronize base Panel table fields (panelName & description)
            if (!trimmedEnglish.isEmpty()) {
                String safePanelName = trimmedEnglish.length() > 20 ? trimmedEnglish.substring(0, 20) : trimmedEnglish;
                String safeDescription = trimmedEnglish.length() > 60 ? trimmedEnglish.substring(0, 60)
                        : trimmedEnglish;
                panel.setPanelName(safePanelName);
                panel.setDescription(safeDescription);
            }

            panel.setSysUserId(userId);
            panelService.update(panel);

            // Synchronize associated SystemModule records if English name changed
            if (oldEnglishName != null && !trimmedEnglish.isEmpty()
                    && !oldEnglishName.equalsIgnoreCase(trimmedEnglish)) {
                updateSystemModulesForPanel(oldEnglishName, trimmedEnglish, userId);
            }
        }
        DisplayListService.getInstance().getFreshList(DisplayListService.ListType.PANELS);
        DisplayListService.getInstance().getFreshList(DisplayListService.ListType.PANELS_ACTIVE);
        DisplayListService.getInstance().getFreshList(DisplayListService.ListType.PANELS_INACTIVE);
    }

    private void updateSystemModulesForPanel(String oldName, String newName, String userId) {
        String[] modulePrefixes = new String[] { "Workplan", "LogbookResults", "ResultValidation" };
        for (String prefix : modulePrefixes) {
            try {
                SystemModule module = systemModuleService.getSystemModuleByName(prefix + ":" + oldName);
                if (module != null) {
                    String rawModuleName = prefix + ":" + newName;
                    String safeModuleName = rawModuleName.length() > 32 ? rawModuleName.substring(0, 32)
                            : rawModuleName;
                    String rawModuleDesc = prefix + "=>panel=>" + newName;
                    String safeModuleDesc = rawModuleDesc.length() > 80 ? rawModuleDesc.substring(0, 80)
                            : rawModuleDesc;

                    module.setSystemModuleName(safeModuleName);
                    module.setDescription(safeModuleDesc);
                    module.setSysUserId(userId);
                    systemModuleService.update(module);
                }
            } catch (Exception e) {
                LogEvent.logError(this.getClass().getSimpleName(), "updateSystemModulesForPanel",
                        "Error updating system module for prefix " + prefix + " from " + oldName + " to " + newName
                                + ": " + e.getMessage());
            }
        }
    }

    @Override
    protected String getPageTitleKey() {
        return null;
    }

    @Override
    protected String getPageSubtitleKey() {
        return null;
    }
}
