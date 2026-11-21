package com.restaurantos.modules.tables.service.impl;
import com.restaurantos.modules.tables.dto.PublicMenuDto.PublicMenu;
import com.restaurantos.modules.tables.repository.PublicQrMenuDao;
import com.restaurantos.modules.tables.service.PublicQrMenuService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service
public class PublicQrMenuServiceImpl implements PublicQrMenuService {
 private final PublicQrMenuDao dao;
 public PublicQrMenuServiceImpl(PublicQrMenuDao dao){this.dao=dao;}
 @Transactional(readOnly=true)
 public PublicMenu resolve(String qrToken){return dao.resolve(qrToken);}
}
