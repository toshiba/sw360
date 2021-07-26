/*
 * Copyright Siemens AG, 2013-2015, 2019. Part of the SW360 Portal Project.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.sw360.portal.portlets.admin;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.thrift.TException;
import org.eclipse.sw360.datahandler.permissions.PermissionUtils;
import org.eclipse.sw360.datahandler.thrift.RequestStatus;
import org.eclipse.sw360.datahandler.thrift.licenses.LicenseService;
import org.eclipse.sw360.datahandler.thrift.licenses.Obligation;
import org.eclipse.sw360.datahandler.thrift.licenses.ObligationElement;
import org.eclipse.sw360.datahandler.thrift.licenses.ObligationNode;
import org.eclipse.sw360.datahandler.thrift.users.User;
import org.eclipse.sw360.datahandler.thrift.users.UserGroup;
import org.eclipse.sw360.portal.common.UsedAsLiferayAction;
import org.eclipse.sw360.portal.portlets.Sw360Portlet;
import org.eclipse.sw360.portal.portlets.components.ComponentPortletUtils;
import org.eclipse.sw360.portal.users.UserCacheHolder;
import org.osgi.service.component.annotations.ConfigurationPolicy;

import javax.portlet.*;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.*;
import com.liferay.portal.kernel.json.JSONFactoryUtil;
import com.liferay.portal.kernel.json.JSONObject;
import com.liferay.portal.kernel.json.JSONException;
import com.liferay.portal.kernel.json.JSONArray;
import static com.google.common.base.Strings.isNullOrEmpty;

import static org.eclipse.sw360.portal.common.PortalConstants.*;

@org.osgi.service.component.annotations.Component(
    immediate = true,
    properties = {
        "/org/eclipse/sw360/portal/portlets/base.properties",
        "/org/eclipse/sw360/portal/portlets/admin.properties"
    },
    property = {
        "javax.portlet.name=" + TODOS_PORTLET_NAME,

        "javax.portlet.display-name=Obligations",
        "javax.portlet.info.short-title=Obligations",
        "javax.portlet.info.title=Obligations",
        "javax.portlet.resource-bundle=content.Language",
        "javax.portlet.init-param.view-template=/html/admin/obligations/view.jsp",
    },
    service = Portlet.class,
    configurationPolicy = ConfigurationPolicy.REQUIRE
)
public class TodoPortlet extends Sw360Portlet {

    private static final Logger log = LogManager.getLogger(TodoPortlet.class);
    String obligationText = "";
    //! Serve resource and helpers
    @Override

    public void serveResource(ResourceRequest request, ResourceResponse response) throws IOException, PortletException {
        String action = request.getParameter(ACTION);
        String what = request.getParameter(WHAT);
        String where = request.getParameter(WHERE);
        final String id = request.getParameter("id");
        final User user = UserCacheHolder.getUserFromRequest(request);

        LicenseService.Iface licenseClient = thriftClients.makeLicenseClient();

        if (REMOVE_TODO.equals(action)) {
            try {
                RequestStatus status = licenseClient.deleteObligations(id, user);
                renderRequestStatus(request,response, status);
            } catch (TException e) {
                log.error("Error deleting oblig", e);
                renderRequestStatus(request,response, RequestStatus.FAILURE);
            }
        } else if (VIEW_IMPORT_OBLIGATION_ELEMENTS.equals(action)) {
            serveObligationElementSearchResults(request, response, where);
        }
    }

    private void serveObligationElementSearchResults(ResourceRequest request, ResourceResponse response, String searchText) throws IOException, PortletException {
        final User user = UserCacheHolder.getUserFromRequest(request);
        List<ObligationElement> searchResult;

        try {
            LicenseService.Iface client = thriftClients.makeLicenseClient();
            if (isNullOrEmpty(searchText)) {
                searchResult = client.getObligationElements();
            } else {
                // need to update for search if input (where)
                searchResult = null;
                //client.search(searchText);
            }
        } catch (TException e) {
            log.error("Error searching Obligation Element", e);
            searchResult = Collections.emptyList();
        }
        request.setAttribute(OBLIGATION_ELEMENT_SEARCH, searchResult);
        include("/html/admin/obligations/ajax/searchObligationElementsAjax.jsp", request, response, PortletRequest.RESOURCE_PHASE);
    }


    //! VIEW and helpers
    @Override
    public void doView(RenderRequest request, RenderResponse response) throws IOException, PortletException {
        String pageName = request.getParameter(PAGENAME);
        if (PAGENAME_ADD.equals(pageName)) {
            List<ObligationNode> obligationNodeList;
            List<ObligationElement> obligationElementList;
            try {
                LicenseService.Iface licenseClient = thriftClients.makeLicenseClient();
                obligationNodeList = licenseClient.getObligationNodes();
                obligationElementList = licenseClient.getObligationElements();
            } catch (Exception e) {
                log.error("Could not get Obligation node from backend ", e);
                obligationNodeList = Collections.emptyList();
                obligationElementList = Collections.emptyList();
            }
            request.setAttribute(OBLIGATION_NODE_LIST, obligationNodeList);
            request.setAttribute(OBLIGATION_ELEMENT_LIST, obligationElementList);
            include("/html/admin/obligations/add.jsp", request, response);
        } else {
            prepareStandardView(request);
            super.doView(request, response);
        }
    }

    private void prepareStandardView(RenderRequest request) {
        List<Obligation> obligList;
        try {
            final User user = UserCacheHolder.getUserFromRequest(request);
            LicenseService.Iface licenseClient = thriftClients.makeLicenseClient();

            obligList = licenseClient.getObligations();
        } catch (TException e) {
            log.error("Could not get Obligation from backend ", e);
            obligList = Collections.emptyList();
        }

        request.setAttribute(TODO_LIST, obligList);
    }

    @UsedAsLiferayAction
    public void addObligations(ActionRequest request, ActionResponse response) {
        final Obligation oblig = new Obligation();
        ComponentPortletUtils.updateTodoFromRequest(request, oblig);

        obligationText = "";
        try {
            JSONObject jsonObject = JSONFactoryUtil.createJSONObject(request.getParameter(Obligation._Fields.TEXT.toString()));
            readNode(request, jsonObject);
            obligationText = builtTextTree(jsonObject, 0);
            oblig.setText(obligationText);
            oblig.setNode(jsonObject.toJSONString());
        } catch (JSONException e) {
            //TODO: handle exception
        }

        try {
            LicenseService.Iface licenseClient = thriftClients.makeLicenseClient();
            final User user = UserCacheHolder.getUserFromRequest(request);
            licenseClient.addObligations(oblig, user);
        } catch (TException e) {
            log.error("Error adding oblig", e);
        }
    }

    // Read node from input
    public void readNode(ActionRequest request, JSONObject jsonObject) {
        JSONArray jsonArray = jsonObject.getJSONArray("val");        
        jsonObject.put("id", addNode(request, jsonArray));
        jsonObject.remove("val");

        for (int i = 0; i < jsonObject.getJSONArray("children").length(); i++) {
            JSONObject contactObject = jsonObject.getJSONArray("children").getJSONObject(i);
            readNode(request, contactObject);
        }
    }
    
    public String addObligationElement(ActionRequest request, ObligationElement obligationElement){
        try {
            LicenseService.Iface licenseClient = thriftClients.makeLicenseClient();
            final User user = UserCacheHolder.getUserFromRequest(request);
            return licenseClient.addObligationElements(obligationElement, user);
        } catch (TException e) {
            log.error("Error adding oblig element", e);
            return "false";
        }
    }
    public String addObligationNode(ActionRequest request, ObligationNode obligationNode){
        try {
            LicenseService.Iface licenseClient = thriftClients.makeLicenseClient();
            final User user = UserCacheHolder.getUserFromRequest(request);
            return licenseClient.addObligationNodes(obligationNode, user);
        } catch (TException e) {
            log.error("Error adding oblig node", e);
            return "false";
        }
    }

    // Add node to DB
    public String addNode(ActionRequest request, JSONArray jsonArray) {
        // check ROOT
        if ( jsonArray.length() == 1 ){
            //System.out.println("> Add Root");
            ObligationNode obligationNode = new ObligationNode();
            obligationNode.setNodeType("ROOT");
            obligationNode.setNodeText(jsonArray.getString(0));
            //System.out.println(obligationNode);
            return addObligationNode(request, obligationNode);
        }

        // check node type and save
        switch(jsonArray.getString(0)) {
            case "Obligation":
                //System.out.println("> Add Obligation Element");
                for (int i = 0; i < jsonArray.length(); i++) {
                    String value = jsonArray.getString(i);
                }
                ObligationElement obligationElement = new ObligationElement();
                obligationElement.setLangElement(jsonArray.getString(1));
                obligationElement.setAction(jsonArray.getString(2));
                obligationElement.setObject(jsonArray.getString(3));
                //System.out.println(obligationElement);

                ObligationNode obligationNodeObl = new ObligationNode();
                obligationNodeObl.setNodeType("Obligation");
                obligationNodeObl.setOblElementId(addObligationElement(request, obligationElement));
                //System.out.println("Obligation Node with type Obligation: " +obligationNodeObl);
                return addObligationNode(request, obligationNodeObl);
            default:
                //System.out.println("> Add Obligation Node");
                ObligationNode obligationNode = new ObligationNode();
                obligationNode.setNodeType(jsonArray.getString(0));
                obligationNode.setNodeText(jsonArray.getString(1));
                //System.out.println(obligationNode);
                return addObligationNode(request, obligationNode);   
        }
    }

    public String builtTextTree(JSONObject jsonObject, int level) {
        StringBuilder prefix = new StringBuilder("");
        for(int j=1; j<level ; j++) {
            prefix = prefix.append("\t");
		}
        try {
            LicenseService.Iface licenseClient = thriftClients.makeLicenseClient();
            ObligationNode obligationNode = licenseClient.getObligationNodeById(jsonObject.get("id").toString());
            String obligationTextNode = "";
            if (obligationNode.getNodeType().equals("ROOT")) {
                obligationTextNode = obligationNode.getNodeText();
                //obligationText = obligationText + obligationTextNode;
            } else {
                if (obligationNode.getNodeType().equals("Obligation")) {
                    ObligationElement obligationElement = licenseClient.getObligationElementById(obligationNode.getOblElementId());
                    obligationTextNode = prefix.toString() + obligationElement.getLangElement() + " " + obligationElement.getAction() + " " + obligationElement.getObject();
                } else {
                    obligationTextNode = prefix.toString() + obligationNode.getNodeType() +" "+ obligationNode.getNodeText();
                }
                obligationText = obligationText + "\n" + obligationTextNode;
            }
        } catch (Exception e) {
            //TODO: handle exception
        }
        if (jsonObject.getJSONArray("children").length() != 0 ) {
            for (int i = 0; i < jsonObject.getJSONArray("children").length(); i++) {
                JSONObject contactObject = jsonObject.getJSONArray("children").getJSONObject(i);
                builtTextTree(contactObject, level+1);
            }
        }

        return obligationText.replaceFirst("\n","");
    }
}
