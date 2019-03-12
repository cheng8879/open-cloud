package com.github.lyd.base.provider.service.impl;

import com.github.lyd.base.client.constants.BaseConstants;
import com.github.lyd.base.client.constants.ResourceType;
import com.github.lyd.base.client.model.BaseResourceMenuDto;
import com.github.lyd.base.client.model.entity.BaseResourceMenu;
import com.github.lyd.base.client.model.entity.BaseResourceOperation;
import com.github.lyd.base.provider.mapper.BaseResourceMenuMapper;
import com.github.lyd.base.provider.service.BaseAuthorityService;
import com.github.lyd.base.provider.service.BaseResourceMenuService;
import com.github.lyd.base.provider.service.BaseResourceOperationService;
import com.github.lyd.common.exception.OpenAlertException;
import com.github.lyd.common.mapper.ExampleBuilder;
import com.github.lyd.common.model.PageList;
import com.github.lyd.common.model.PageParams;
import com.github.pagehelper.PageHelper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tk.mybatis.mapper.entity.Example;

import java.util.Date;
import java.util.List;

/**
 * @author liuyadu
 */
@Slf4j
@Service
@Transactional(rollbackFor = Exception.class)
public class BaseResourceMenuServiceImpl implements BaseResourceMenuService {
    @Autowired
    private BaseResourceMenuMapper baseResourceMenuMapper;
    @Autowired
    private BaseAuthorityService baseAuthorityService;
    @Autowired
    private BaseResourceOperationService baseResourceOperationService;

    /**
     * 分页查询
     *
     * @param pageParams
     * @param keyword
     * @return
     */
    @Override
    public PageList<BaseResourceMenu> findListPage(PageParams pageParams, String keyword) {
        PageHelper.startPage(pageParams.getPage(), pageParams.getLimit(), pageParams.getOrderBy());
        ExampleBuilder builder = new ExampleBuilder(BaseResourceMenu.class);
        Example example = builder.criteria()
                .orLike("menuCode", keyword)
                .orLike("menuName", keyword).end().build();
        example.orderBy("menuId").asc().orderBy("priority").asc();
        List<BaseResourceMenu> list = baseResourceMenuMapper.selectByExample(example);
        return new PageList(list);
    }

    /**
     * 查询列表
     *
     * @param keyword
     * @return
     */
    @Override
    public PageList<BaseResourceMenu> findAllList(String keyword) {
        ExampleBuilder builder = new ExampleBuilder(BaseResourceMenu.class);
        Example example = builder.criteria()
                .orLike("menuCode", keyword)
                .orLike("menuName", keyword).end().build();
        example.orderBy("menuId").asc().orderBy("priority").asc();
        List<BaseResourceMenu> list = baseResourceMenuMapper.selectByExample(example);
        return new PageList(list);
    }

    /**
     * 获取菜单和操作列表
     *
     * @param keyword
     * @return
     */
    @Override
    public PageList<BaseResourceMenuDto> findWithActionList(String keyword) {
        List<BaseResourceMenuDto> list = baseResourceMenuMapper.selectWithActionList();
        return new PageList(list);
    }

    /**
     * 根据主键获取菜单
     *
     * @param menuId
     * @return
     */
    @Override
    public BaseResourceMenu getMenu(Long menuId) {
        return baseResourceMenuMapper.selectByPrimaryKey(menuId);
    }

    /**
     * 检查菜单编码是否存在
     *
     * @param menuCode
     * @return
     */
    @Override
    public Boolean isExist(String menuCode) {
        ExampleBuilder builder = new ExampleBuilder(BaseResourceMenu.class);
        Example example = builder.criteria()
                .andEqualTo("menuCode", menuCode)
                .end().build();
        int count = baseResourceMenuMapper.selectCountByExample(example);
        return count > 0 ? true : false;
    }

    /**
     * 添加菜单资源
     *
     * @param menu
     * @return
     */
    @Override
    public Long addMenu(BaseResourceMenu menu) {
        if (isExist(menu.getMenuCode())) {
            throw new OpenAlertException(String.format("%s编码已存在!", menu.getMenuCode()));
        }
        if (menu.getParentId() == null) {
            menu.setParentId(0L);
        }
        if (menu.getPriority() == null) {
            menu.setPriority(0);
        }
        if (menu.getStatus() == null) {
            menu.setStatus(BaseConstants.ENABLED);
        }
        if (menu.getIsPersist() == null) {
            menu.setIsPersist(BaseConstants.DISABLED);
        }
        menu.setCreateTime(new Date());
        menu.setUpdateTime(menu.getCreateTime());
        baseResourceMenuMapper.insertSelective(menu);
        // 同步权限表里的信息
        baseAuthorityService.saveOrUpdateAuthority(menu.getMenuId(), ResourceType.menu);

        BaseResourceOperation browse = new BaseResourceOperation();
        browse.setMenuId(menu.getMenuId());
        browse.setOperationCode(menu.getMenuCode()+"Browse");
        browse.setOperationName("浏览");
        browse.setOperationDesc(menu.getMenuName()+browse.getOperationName());
        browse.setIsPersist(1);
        browse.setStatus(1);
        BaseResourceOperation create = new BaseResourceOperation();
        create.setMenuId(menu.getMenuId());
        create.setOperationCode(menu.getMenuName()+menu.getMenuCode()+"Create");
        create.setOperationName("创建");
        create.setOperationDesc(create.getOperationName());
        create.setIsPersist(1);
        create.setStatus(1);
        BaseResourceOperation edit = new BaseResourceOperation();
        edit.setMenuId(menu.getMenuId());
        edit.setOperationCode(menu.getMenuCode()+"Edit");
        edit.setOperationName("编辑");
        edit.setOperationDesc(menu.getMenuName()+edit.getOperationName());
        edit.setIsPersist(1);
        edit.setStatus(1);
        BaseResourceOperation remove = new BaseResourceOperation();
        remove.setMenuId(menu.getMenuId());
        remove.setOperationName("删除");
        remove.setOperationDesc(menu.getMenuName()+remove.getOperationName());
        remove.setOperationCode(menu.getMenuCode()+"Remove");
        remove.setIsPersist(1);
        remove.setStatus(1);
       try {
           baseResourceOperationService.addOperation(browse);
           baseResourceOperationService.addOperation(create);
           baseResourceOperationService.addOperation(edit);
           baseResourceOperationService.addOperation(remove);
       }catch (Exception e){
           log.error("");
       }
        return menu.getMenuId();
    }

    /**
     * 修改菜单资源
     *
     * @param menu
     * @return
     */
    @Override
    public void updateMenu(BaseResourceMenu menu) {
        BaseResourceMenu saved = getMenu(menu.getMenuId());
        if (saved == null) {
            throw new OpenAlertException(String.format("%s信息不存在!", menu.getMenuId()));
        }
        if (!saved.getMenuCode().equals(menu.getMenuCode())) {
            // 和原来不一致重新检查唯一性
            if (isExist(menu.getMenuCode())) {
                throw new OpenAlertException(String.format("%s编码已存在!", menu.getMenuCode()));
            }
        }
        if (menu.getParentId() == null) {
            menu.setParentId(0l);
        }
        if (menu.getPriority() == null) {
            menu.setPriority(0);
        }
        menu.setUpdateTime(new Date());
        baseResourceMenuMapper.updateByPrimaryKeySelective(menu);
        // 同步权限表里的信息
        baseAuthorityService.saveOrUpdateAuthority(menu.getMenuId(), ResourceType.menu);
    }

    /**
     * 更新启用禁用
     *
     * @param menuId
     * @param status
     * @return
     */
    @Override
    public void updateStatus(Long menuId, Integer status) {
        BaseResourceMenu menu = new BaseResourceMenu();
        menu.setMenuId(menuId);
        menu.setStatus(status);
        menu.setUpdateTime(new Date());
        baseResourceMenuMapper.updateByPrimaryKeySelective(menu);
        // 同步权限表里的信息
        baseAuthorityService.saveOrUpdateAuthority(menu.getMenuId(), ResourceType.menu);
    }

    /**
     * 移除菜单
     *
     * @param menuId
     * @return
     */
    @Override
    public void removeMenu(Long menuId) {
        BaseResourceMenu menu = getMenu(menuId);
        if (menu != null && menu.getIsPersist().equals(BaseConstants.ENABLED)) {
            throw new OpenAlertException(String.format("保留数据,不允许删除!"));
        }
       if (baseAuthorityService.isGranted(menuId, ResourceType.menu)) {
            throw new OpenAlertException(String.format("资源已被授权,不允许删除!取消授权后,再次尝试!"));
        }
        baseAuthorityService.removeAuthority(menuId,ResourceType.menu);
        baseResourceMenuMapper.deleteByPrimaryKey(menuId);
    }


}
