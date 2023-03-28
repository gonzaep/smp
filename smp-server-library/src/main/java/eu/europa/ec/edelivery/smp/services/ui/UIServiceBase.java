package eu.europa.ec.edelivery.smp.services.ui;

import eu.europa.ec.edelivery.smp.data.dao.BaseDao;
import eu.europa.ec.edelivery.smp.data.model.BaseEntity;
import eu.europa.ec.edelivery.smp.data.ui.ServiceResult;
import eu.europa.ec.edelivery.smp.exceptions.SMPRuntimeException;
import eu.europa.ec.edelivery.smp.logging.SMPLogger;
import eu.europa.ec.edelivery.smp.logging.SMPLoggerFactory;
import org.apache.commons.beanutils.BeanUtils;
import org.springframework.core.GenericTypeResolver;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;

import static eu.europa.ec.edelivery.smp.exceptions.ErrorCode.INTERNAL_ERROR;

/**
 * @author Joze Rihtarsic
 * @since 4.1
 */
abstract class UIServiceBase<E extends BaseEntity, R> {

    private static final SMPLogger LOG = SMPLoggerFactory.getLogger(UIServiceBase.class);

    private final Class<R> roClass;
    private final Class<E> dbClass;


    public UIServiceBase() {
        Class[] clsArg = GenericTypeResolver.resolveTypeArguments(getClass(), UIServiceBase.class);
        roClass =(Class<R>)clsArg[1];
        dbClass =(Class<E>)clsArg[0];

    }

    /**
     * Method returns UI  resource object list for page.
     *
     * @param page
     * @param pageSize
     * @param sortField
     * @param sortOrder
     * @return
     */
    public ServiceResult<R> getTableList(int page, int pageSize,
                                         String sortField,
                                         String sortOrder,
                                         Object filter) {

        ServiceResult<R> sg = new ServiceResult<>();
        sg.setPage(page<0?0:page);

        long iCnt = getDatabaseDao().getDataListCount(filter);
        if (pageSize < 0) { // if page size iz -1 return all results and set pageSize to maxCount
            pageSize = (int)iCnt;
        }
        sg.setPageSize(pageSize);
        sg.setCount(iCnt);

        if (iCnt > 0) {
            int iStartIndex = pageSize<0?-1:page * pageSize;
            if (iStartIndex >= iCnt && page > 0){
                page = page -1;
                sg.setPage(page); // go back for a page
                iStartIndex = pageSize<0?-1:page * pageSize;
            }



            List<E> lst = getDatabaseDao().getDataList(iStartIndex, pageSize, sortField, sortOrder, filter);

            List<R>  lstRo = new ArrayList<>();
            for (E d : lst) {
                R dro = convertToRo(d);
                 try {
                    BeanUtils.setProperty(dro, "index", iStartIndex++);
                    lstRo.add(dro);
                } catch (InvocationTargetException | IllegalAccessException e) {
                     String msg = "Error occurred while retrieving list for " +roClass.getName();
                    LOG.error(msg, e );
                     throw new SMPRuntimeException(INTERNAL_ERROR, "DB list query exception.", msg);
                }
            }
            sg.getServiceEntities().addAll(lstRo);
        }
        return sg;
    }


    /**
     * Simple method for converting types. Property name and property type must match
     * @param d
     * @return
     */
    public R convertToRo(E d) {
        try {
            R dro = roClass.newInstance();
            BeanUtils.copyProperties(dro, d);
            return dro;
         } catch (InstantiationException | InvocationTargetException | IllegalAccessException e) {
            String msg = "Error occurred while converting to RO Entity for " +roClass.getName();
            LOG.error(msg, e );
            throw new SMPRuntimeException(INTERNAL_ERROR, "DB to RO entity conversion.", msg);
        }
    }
    /*
     * Simple method for converting types. Property name and property type must match
     * @param d
     * @return
             */
    public E convertFromRo(R d) {
        try {
            E e = dbClass.newInstance();
            BeanUtils.copyProperties(e, d);
            return e;
        } catch (InstantiationException | InvocationTargetException | IllegalAccessException e) {
            String msg = "Error occurred while converting to DB entity for " +dbClass.getName();
            LOG.error(msg, e );
            throw new SMPRuntimeException(INTERNAL_ERROR, "RO to DB entity conversion.", msg);
        }
    }

    protected abstract BaseDao<E> getDatabaseDao();
}
