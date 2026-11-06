import {describe,it,expect,vi,afterEach} from 'vitest'
import {defineComponent} from 'vue'
import {mount} from '@vue/test-utils'
import L from 'leaflet'
import {useAreaDrawingRuntime} from './useAreaDrawingRuntime'

vi.mock('@/locales',()=>({t:(key,{number}={})=>`${key} ${number || ''}`}))
vi.mock('@/maps/runtime/vectorEngineRegistry',()=>({getVectorEngine:()=>({
 maplibregl:{Marker:class {
  constructor({element}){this.element=element;this.on=vi.fn();this.remove=vi.fn(()=>this.element.remove())}
  setLngLat(value){this.position=value;return this}
  getElement(){return this.element}
  addTo(map){map.getContainer().append(this.element);return this}
 }},
 maplibreLayerUtils:{
  removeLayers:(map,ids)=>ids.forEach(id=>delete map.layers[id]),
  removeSources:(map,ids)=>ids.forEach(id=>delete map.sources[id]),
  getMapLibreSource:(map,id)=>map.sources[id],
  ensureGeoJsonSource:(map,id,data)=>{map.sources[id]={data,setData(value){this.data=value}}},
  hasMapLibreLayer:(map,id)=>Boolean(map.layers[id]),
  ensureLayer:(map,layer)=>{map.layers[layer.id]=layer}
 }
})}))
let wrapper, runtime, map, container
const layer=()=>({addTo:vi.fn(function(){return this}),remove:vi.fn(),setLatLng:vi.fn(),setBounds:vi.fn(),on:vi.fn()})
vi.mock('leaflet',()=>({default:{point:(x,y)=>({x,y}),polyline:vi.fn(()=>layer()),polygon:vi.fn(()=>layer()),marker:vi.fn(()=>layer()),divIcon:vi.fn(options=>options)}}))
const create=(mode='RASTER')=>{
 container=document.createElement('div');document.body.append(container)
 let enabled=true
 map={__gpEngineMode:'RASTER',getContainer:()=>container,containerPointToLatLng:({x,y})=>({lng:30+x/1000,lat:50+y/1000}),latLngToContainerPoint:([lat,lng])=>({x:(lng-30)*1000,y:(lat-50)*1000}),removeLayer:vi.fn(),dragging:{enabled:()=>enabled,enable:()=>{enabled=true},disable:()=>{enabled=false}}}
 if (mode==='VECTOR') {
  map.__gpEngineMode='VECTOR'
  map.dragPan={isEnabled:()=>enabled,enable:()=>{enabled=true},disable:()=>{enabled=false}}
  delete map.dragging
  map.unproject=([x,y])=>({lng:30+x/1000,lat:50+y/1000})
  map.project=([lng,lat])=>({x:(lng-30)*1000,y:(lat-50)*1000})
  map.sources={};map.layers={};map.on=vi.fn();map.off=vi.fn()
 }
 let wheelEnabled=true
 map[mode==='RASTER'?'scrollWheelZoom':'scrollZoom']={
  enabled:()=>wheelEnabled,isEnabled:()=>wheelEnabled,
  enable:()=>{wheelEnabled=true},disable:()=>{wheelEnabled=false}
 }
 const created=vi.fn()
 wrapper=mount(defineComponent({setup(){runtime=useAreaDrawingRuntime({onRectangleCreated:created});return()=>null}}))
 runtime.initialize(map);runtime.startDrawing('polygon')
 return created
}
const vertexIcons=()=>L.divIcon.mock.calls.filter(([options])=>options.className==='gp-area-vertex-wrapper').map(([options])=>options.html)
const click=(x,y)=>container.dispatchEvent(new MouseEvent('mousedown',{bubbles:true,clientX:x,clientY:y}))
const pointer=(type,x,y,target=container)=>{
 const event=new MouseEvent(type,{bubbles:true,cancelable:true,clientX:x,clientY:y,button:0})
 Object.defineProperties(event,{pointerId:{value:1},pointerType:{value:'mouse'}})
 target.dispatchEvent(event)
}
const dragEdge=(x,y,toX,toY)=>{
 pointer('pointerdown',x,y)
 pointer('pointermove',toX,toY,document)
 pointer('pointerup',toX,toY,document)
}
afterEach(()=>{wrapper?.unmount();container?.remove();vi.clearAllMocks()})
describe('polygon drawing lifecycle',()=>{
 it.each(['RASTER','VECTOR'])('keeps wheel zoom available when drawing and redrawing in %s mode',(mode)=>{
  create(mode)
  const wheel=mode==='RASTER'?map.scrollWheelZoom:map.scrollZoom
  for (const shape of ['rectangle','polygon']) {
    runtime.stopDrawing()
    runtime.startDrawing(shape)
    expect(wheel.isEnabled()).toBe(true)
    expect(mode==='RASTER'?map.dragging.enabled():map.dragPan.isEnabled()).toBe(false)
    runtime.stopDrawing()
    expect(wheel.isEnabled()).toBe(true)
  }
  wheel.disable()
  runtime.startDrawing('polygon')
  expect(wheel.isEnabled()).toBe(false)
  runtime.stopDrawing()
  expect(wheel.isEnabled()).toBe(false)
 })
 it('adds a handle at every vertex and emits a closed polygon after undo',()=>{
  const created=create()
  click(0,0);click(100,0);click(100,100);click(0,100)
  expect(L.marker.mock.results.filter(({value}) => value.remove.mock.calls.length === 0)).toHaveLength(4)
  runtime.undoPoint();expect(runtime.drawingState.value.vertices).toHaveLength(3)
  runtime.finishPolygon()
  expect(created).toHaveBeenCalledOnce()
  expect(created.mock.calls[0][0].geometry.coordinates[0]).toEqual([[30,50],[30.1,50],[30.1,50.1],[30,50]])
  expect(map.dragging.enabled()).toBe(true)
  expect(runtime.isDrawing()).toBe(false)
 })
 it('continues adding corners after the third point and closes only when finished',()=>{
  const created=create()
  const points=[[0,0],[100,0],[100,100],[50,150],[0,100]]
  for (let i=0;i<points.length;i++) {
    click(...points[i])
    expect(runtime.drawingState.value.vertices).toHaveLength(i+1)
    expect(runtime.isDrawing()).toBe(true)
    expect(created).not.toHaveBeenCalled()
    expect(L.polyline.mock.lastCall[0][0]).toHaveLength(i+1)
  }
  runtime.finishPolygon()
  const ring=created.mock.calls[0][0].geometry.coordinates[0]
  expect(ring).toHaveLength(6)
  expect(ring[0]).toEqual(ring.at(-1))
 })
 it('does not finish when the click adding a corner lands on its newly created handle',()=>{
  const created=create()
  click(0,0);click(100,0);click(100,100)
  const last=L.divIcon.mock.calls.at(-1)[0].html
  last.dispatchEvent(new MouseEvent('click',{bubbles:true,detail:1}))
  expect(created).not.toHaveBeenCalled()
  expect(runtime.isDrawing()).toBe(true)
  click(0,100)
  expect(runtime.drawingState.value.vertices).toHaveLength(4)
 })
 it('retains invalid drafts and restores originally disabled interactions on Escape',()=>{
  const created=create()
  runtime.stopDrawing();map.dragging.disable();runtime.startDrawing('polygon')
  click(0,0);click(100,100);click(0,100);click(100,0)
  runtime.finishPolygon()
  expect(created).not.toHaveBeenCalled()
  expect(runtime.drawingState.value.error).toBe('crossing')
  expect(runtime.isDrawing()).toBe(true)
  document.dispatchEvent(new KeyboardEvent('keydown',{key:'Escape'}))
  expect(runtime.isDrawing()).toBe(false)
  expect(map.dragging.enabled()).toBe(false)
 })
 it('keeps the boundary fixed and removes the pointer guide when leaving for the toolbar',()=>{
  const created=create()
  click(0,0);click(100,0);click(100,100)
  const outline=[[50,30],[50,30.1],[50.1,30.1]]
  container.dispatchEvent(new MouseEvent('mousemove',{bubbles:true,clientX:900,clientY:900}))
  expect(L.polyline.mock.lastCall[0][0]).toEqual(outline)
  expect(L.polyline.mock.lastCall[0]).toHaveLength(2)
  container.dispatchEvent(new MouseEvent('mouseleave'))
  expect(L.polyline.mock.lastCall[0]).toEqual([outline])
  runtime.finishPolygon()
  expect(created.mock.calls[0][0].geometry.coordinates[0]).toEqual([[30,50],[30.1,50],[30.1,50.1],[30,50]])
 })
 it('finishes by clicking the last corner without adding a toolbar coordinate',()=>{
  const created=create()
  click(0,0);click(100,0);click(100,100)
  const icons=vertexIcons()
  icons.at(-1).click()
  expect(created).toHaveBeenCalledOnce()
  expect(created.mock.calls[0][0].geometry.coordinates[0]).toHaveLength(4)
  expect(runtime.isDrawing()).toBe(false)
 })
 it('adjusts existing vertices without adding points, drawing guides or closing on vertex clicks',()=>{
  const created=create()
  const geometry={type:'Polygon',coordinates:[[[30,50],[31,50],[30,51],[30,50]]]}
  runtime.startEditing(geometry)
  expect(runtime.drawingState.value.isAdjusting).toBe(true)
  expect(map.dragging.enabled()).toBe(true)
  click(500,500)
  expect(runtime.drawingState.value.vertices).toHaveLength(3)
  const before=L.polyline.mock.calls.length
  container.dispatchEvent(new MouseEvent('mousemove',{bubbles:true,clientX:900,clientY:900}))
  expect(L.polyline.mock.calls).toHaveLength(before)
  const first=vertexIcons().at(-3)
  first.click()
  expect(created).not.toHaveBeenCalled()
  first.dispatchEvent(new KeyboardEvent('keydown',{key:'ArrowRight',bubbles:true}))
  expect(runtime.drawingState.value.vertices[0][0]).toBeCloseTo(30.001)
  expect(geometry.coordinates[0][0][0]).toBe(30)
  runtime.undoPoint()
  expect(runtime.drawingState.value.vertices).toHaveLength(3)
  runtime.finishPolygon()
  expect(created).toHaveBeenCalledOnce()
  expect(created.mock.calls[0][0].geometry.coordinates[0]).toHaveLength(4)
 })
 it.each(['RASTER','VECTOR'])('reshapes an edge without permanent extra handles in %s mode',(mode)=>{
  const created=create(mode)
  const geometry={type:'Polygon',coordinates:[[[30,50],[31,50],[31,51],[30,51],[30,50]]]}
  runtime.startEditing(geometry)
  dragEdge(500,0,500,-100)
  expect(runtime.drawingState.value.vertices).toEqual([[30,50],[30.5,49.9],[31,50],[31,51],[30,51]])
  expect(geometry.coordinates[0]).toHaveLength(5)
  expect(container.querySelector('.gp-area-midpoint')).toBeNull()
  expect(mode==='RASTER'?map.dragging.enabled():map.dragPan.isEnabled()).toBe(true)
  runtime.finishPolygon()
  expect(created.mock.calls[0][0].geometry.coordinates[0]).toHaveLength(6)
 })
 it('supports splitting the next edge with a keyboard shortcut',()=>{
  create()
  runtime.startEditing({type:'Polygon',coordinates:[[[30,50],[31,50],[31,51],[30,51],[30,50]]]})
  vertexIcons().slice(-4)[0].dispatchEvent(new KeyboardEvent('keydown',{key:'+',bubbles:true}))
  expect(runtime.drawingState.value.vertices).toEqual([[30,50],[30.5,50],[31,50],[31,51],[30,51]])
 })
 it('does not insert a vertex on a click or interfere with panning away from an edge',()=>{
  create()
  runtime.startEditing({type:'Polygon',coordinates:[[[30,50],[31,50],[30,51],[30,50]]]})
  pointer('pointerdown',500,0);pointer('pointerup',500,0,document)
  expect(runtime.drawingState.value.vertices).toHaveLength(3)
  pointer('pointerdown',200,200)
  expect(map.dragging.enabled()).toBe(true)
  pointer('pointermove',220,220,document);pointer('pointerup',220,220,document)
  expect(runtime.drawingState.value.vertices).toHaveLength(3)
 })
 it('can reshape the closing edge and discard an interrupted drag',()=>{
  const created=create()
  const geometry={type:'Polygon',coordinates:[[[30,50],[31,50],[30,51],[30,50]]]}
  runtime.startEditing(geometry)
  pointer('pointerdown',0,500);pointer('pointermove',-100,500,document)
  expect(runtime.drawingState.value.vertices).toEqual([[30,50],[31,50],[30,51],[29.9,50.5]])
  pointer('pointercancel',-100,500,document)
  expect(runtime.drawingState.value.vertices).toHaveLength(3)
  expect(map.dragging.enabled()).toBe(true)
  runtime.stopDrawing()
  expect(created).not.toHaveBeenCalled()
  expect(geometry.coordinates[0]).toHaveLength(4)
 })
 it('keeps the vertex limit when an edge is dragged',()=>{
  create()
  const vertices=Array.from({length:500},(_,i)=>[30+10*Math.cos(i*2*Math.PI/500),50+10*Math.sin(i*2*Math.PI/500)])
  runtime.startEditing({type:'Polygon',coordinates:[[...vertices,vertices[0]]]})
  const a=map.latLngToContainerPoint([vertices[0][1],vertices[0][0]])
  const b=map.latLngToContainerPoint([vertices[1][1],vertices[1][0]])
  dragEdge((a.x+b.x)/2,(a.y+b.y)/2,(a.x+b.x)/2+20,(a.y+b.y)/2)
  expect(runtime.drawingState.value.vertices).toHaveLength(500)
  expect(runtime.drawingState.value.error).toBe('vertices')
 })
 it('copies saved vertices for adjustment so cancel cannot mutate the original',()=>{
  create()
  const geometry={type:'Polygon',coordinates:[[[30,50],[31,50],[30,51],[30,50]]]}
  runtime.startEditing(geometry)
  runtime.drawingState.value.vertices[1][0]=30.5
  runtime.stopDrawing()
  expect(geometry.coordinates[0][1][0]).toBe(31)
 })
})
