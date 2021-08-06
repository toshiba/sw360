<%--
  ~ Copyright Toshiba corporation, 2021. Part of the SW360 Portal Project.
  ~ Copyright Toshiba Software Development (Vietnam) Co., Ltd., 2021. Part of the SW360 Portal Project.
  ~
  ~ This program and the accompanying materials are made
  ~ available under the terms of the Eclipse Public License 2.0
  ~ which is available at https://www.eclipse.org/legal/epl-2.0/
  ~
  ~ SPDX-License-Identifier: EPL-2.0
  --%>

<%@include file="/html/init.jsp"%>

<portlet:defineObjects />
<liferay-theme:defineObjects />

<jsp:useBean id="obligationElementSearch" type="java.util.List<org.eclipse.sw360.datahandler.thrift.licenses.ObligationElement>" scope="request"/>
<core_rt:if test="${obligationElementSearch.size()>0}" >
    <core_rt:forEach items="${obligationElementSearch}" var="entry">
        <tr>
            <td>
                <div class="form-check">
                    <input type="radio" class="form-check-input" name="<portlet:namespace/>obligationElementId" lang="${entry.langElement}" action="${entry.action}" object="${entry.object}">
                </div>
            </td>
            <td>
                <sw360:out value="${entry.langElement}"/>
            </td>
            <td>
                <sw360:out value="${entry.action}"/>
            </td>
            <td>
                <sw360:out value="${entry.object}"/>
            </td>
        </tr>
    </core_rt:forEach>
</core_rt:if>
