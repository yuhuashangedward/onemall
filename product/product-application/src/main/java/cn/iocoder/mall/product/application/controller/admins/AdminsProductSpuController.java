package cn.iocoder.mall.product.application.controller.admins;

import cn.iocoder.common.framework.vo.CommonResult;
import cn.iocoder.mall.admin.sdk.context.AdminSecurityContextHolder;
import cn.iocoder.mall.product.api.ProductSpuService;
import cn.iocoder.mall.product.api.bo.ProductSpuDetailBO;
import cn.iocoder.mall.product.api.bo.ProductSpuPageBO;
import cn.iocoder.mall.product.api.dto.ProductSkuAddOrUpdateDTO;
import cn.iocoder.mall.product.api.dto.ProductSpuAddDTO;
import cn.iocoder.mall.product.api.dto.ProductSpuPageDTO;
import cn.iocoder.mall.product.api.dto.ProductSpuUpdateDTO;
import cn.iocoder.mall.product.application.convert.ProductSpuConvert;
import cn.iocoder.mall.product.application.vo.admins.AdminsProductSpuDetailVO;
import cn.iocoder.mall.product.application.vo.admins.AdminsProductSpuPageVO;
import org.apache.dubbo.config.annotation.Reference;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiImplicitParams;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("admins")
@Api("商品 SPU + SKU")
public class AdminsProductSpuController {

    @Reference(validation = "true")
    @Autowired
    private ProductSpuService productSpuService;

    @Autowired
    private ObjectMapper objectMapper; // jackson 解析

    @PostMapping("/spu/add")
    @ApiOperation("创建商品")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "name", value = "SPU 名字", required = true, example = "厮大牛逼"),
            @ApiImplicitParam(name = "sellPoint", value = "卖点", required = true, example = "各种 MQ 骚操作"),
            @ApiImplicitParam(name = "description", value = "描述", required = true, example = "你就说强不强 MQ 骚操作"),
            @ApiImplicitParam(name = "cid", value = "分类编号", required = true, example = "反正我是信了"),
            @ApiImplicitParam(name = "picUrls", value = "商品主图地址的数组", required = true, example = "http://www.iocoder.cn"),
            @ApiImplicitParam(name = "visible", value = "是否上架商品（是否可见）", required = true, example = "true"),
            @ApiImplicitParam(name = "skuStr", value = "SKU 字符串", required = true, example = "[{\"attrs\": [1,3 ], \"price\": 1, \"quantity\": 100, \"picUrl\": \"http://www.iocoder.cn\"}]"),

    })
    public CommonResult<AdminsProductSpuDetailVO> add(@RequestParam("name") String name,
                                                      @RequestParam("sellPoint") String sellPoint,
                                                      @RequestParam("description") String description,
                                                      @RequestParam("cid") Integer cid,
                                                      @RequestParam("picUrls") List<String> picUrls,
                                                      @RequestParam("visible") Boolean visible,
                                                      @RequestParam("skuStr") String skuStr) { // TODO 芋艿，因为考虑不使用 json 接受参数，所以这里手动转。
        // 创建 ProductSpuAddDTO 对象
        ProductSpuAddDTO productSpuAddDTO = new ProductSpuAddDTO().setName(name).setSellPoint(sellPoint)
                .setDescription(description).setCid(cid).setPicUrls(picUrls).setVisible(visible)
                .setSkus(parseSkus(skuStr, ProductSkuAddOrUpdateDTO.class));
        // 保存商品
        CommonResult<ProductSpuDetailBO> result = productSpuService.addProductSpu(AdminSecurityContextHolder.getContext().getAdminId(), productSpuAddDTO);
        // 返回结果
        return ProductSpuConvert.INSTANCE.convert(result);
    }

    @PostMapping("/spu/update")
    @ApiOperation("更新商品")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "id", value = "SPU 编号", required = true, example = "100"),
            @ApiImplicitParam(name = "name", value = "SPU 名字", required = true, example = "厮大牛逼"),
            @ApiImplicitParam(name = "sellPoint", value = "卖点", required = true, example = "各种 MQ 骚操作"),
            @ApiImplicitParam(name = "description", value = "描述", required = true, example = "你就说强不强 MQ 骚操作"),
            @ApiImplicitParam(name = "cid", value = "分类编号", required = true, example = "反正我是信了"),
            @ApiImplicitParam(name = "picUrls", value = "商品主图地址的数组", required = true, example = "http://www.iocoder.cn"),
            @ApiImplicitParam(name = "visible", value = "是否上架商品（是否可见）", required = true, example = "true"),
            @ApiImplicitParam(name = "skuStr", value = "SKU 字符串", required = true, example = "[{\"attrs\": [1,3 ], \"price\": 1, \"quantity\": 100, \"picUrl\": \"http://www.iocoder.cn\"}]"),

    })
    public CommonResult<Boolean> update(@RequestParam("id") Integer id,
                                        @RequestParam("name") String name,
                                        @RequestParam("sellPoint") String sellPoint,
                                        @RequestParam("description") String description,
                                        @RequestParam("cid") Integer cid,
                                        @RequestParam("picUrls") List<String> picUrls,
                                        @RequestParam("visible") Boolean visible,
                                        @RequestParam("skuStr") String skuStr) { // TODO 芋艿，因为考虑不使用 json 接受参数，所以这里手动转。
        // 创建 ProductSpuUpdateDTO 对象
        ProductSpuUpdateDTO productSpuUpdateDTO = new ProductSpuUpdateDTO().setId(id).setName(name).setSellPoint(sellPoint)
                .setDescription(description).setCid(cid).setPicUrls(picUrls).setVisible(visible)
                .setSkus(parseSkus(skuStr, ProductSkuAddOrUpdateDTO.class));
        // 更新商品
        return productSpuService.updateProductSpu(AdminSecurityContextHolder.getContext().getAdminId(), productSpuUpdateDTO);
    }

    @PostMapping("/spu/update_sort")
    @ApiOperation("更新商品的排序")
    public CommonResult<Boolean> updateSort(@RequestParam("id") Integer id,
                                            @RequestParam("sort") Integer sort) {
        return productSpuService.updateProductSpuSort(AdminSecurityContextHolder.getContext().getAdminId(), id, sort);
    }

    // TODO 芋艿，删除功能暂时不做。主要原因是，关联的数据太多。删除带来的问题会比较大

    @GetMapping("/spu/page")
    @ApiOperation("商品 SPU 分页列表")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "name", value = "商品名称，模糊匹配", example = "小王"),
            @ApiImplicitParam(name = "pageNo", value = "页码，从 1 开始", example = "1"),
            @ApiImplicitParam(name = "pageSize", value = "每页条数", required = true, example = "10"),
    })
    public CommonResult<AdminsProductSpuPageVO> spuPage(@RequestParam(value = "name", required = false) String name,
                                                        @RequestParam(value = "pageNo", defaultValue = "0") Integer pageNo,
                                                        @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize) {
        // 创建 ProductSpuPageDTO 对象
        ProductSpuPageDTO productSpuPageDTO = new ProductSpuPageDTO().setName(name).setPageNo(pageNo).setPageSize(pageSize);
        CommonResult<ProductSpuPageBO> result = productSpuService.getProductSpuPage(productSpuPageDTO);
        return ProductSpuConvert.INSTANCE.convert2(result);
    }

    @GetMapping("/spu/info")
    @ApiOperation("商品 SPU 明细")
    @ApiImplicitParam(name = "id", value = "SPU 编号", required = true, example = "100")
    public CommonResult<AdminsProductSpuDetailVO> info(@RequestParam("id") Integer id) {
        return ProductSpuConvert.INSTANCE.convert(productSpuService.getProductSpuDetail(id));
    }

    private <T> List<T> parseSkus(String skuStr, Class<T> clazz) {
        JavaType type  = objectMapper.getTypeFactory().constructParametricType(List.class, clazz);
        try {
            return objectMapper.readValue(skuStr, type);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

}
